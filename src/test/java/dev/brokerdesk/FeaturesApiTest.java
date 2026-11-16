package dev.brokerdesk;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Market history, analytics, news, favorites, alerts, wallet, liquidity, applications and the live stream, over real HTTP. Own context: nothing shared with other classes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"spring.profiles.active=demo", "test.context=features"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FeaturesApiTest {
    @Autowired TestRestTemplate rest;
    @LocalServerPort int port;

    ResponseEntity<JsonNode> call(String user, HttpMethod m, String url, Object body) {
        return rest.withBasicAuth(user, "demo-pass").exchange(url, m, body == null ? null : new HttpEntity<>(body), JsonNode.class);
    }
    JsonNode get(String user, String url) { return call(user, HttpMethod.GET, url, null).getBody(); }
    Map<String, Object> order(String cid, String side, long qty, long price) { return Map.of("clientOrderId", cid, "symbol", "SBNK", "side", side, "quantity", qty, "limitPriceMills", price); }

    @Test @Order(1) void marketHeaderAndBreadth() {
        var s = get("trader1", "/api/v1/market/summary");
        assertThat(s.get("indexPoints").asLong()).isBetween(9_000L, 11_000L);
        assertThat(s.get("advancers").asInt() + s.get("decliners").asInt() + s.get("unchanged").asInt()).isEqualTo(12);
        assertThat(s.get("session").asText()).isIn("OPEN", "CLOSED");
        assertThat(s.get("trades").asLong()).isPositive();
    }

    @Test @Order(2) void candlesEndAtThePreviousCloseAndRangesNarrow() {
        var month = get("trader1", "/api/v1/market/SBNK/candles?range=1M");
        assertThat(month.size()).isBetween(17, 24);
        assertThat(month.get(month.size() - 1).get("close").asLong()).isEqualTo(12_500);   // last candle = previous close = reference price
        for (JsonNode c : month) assertThat(c.get("high").asLong()).isGreaterThanOrEqualTo(Math.max(c.get("open").asLong(), c.get("close").asLong()));
        assertThat(get("trader1", "/api/v1/market/SBNK/candles?range=ALL").size()).isGreaterThan(700);
        assertThat(get("trader1", "/api/v1/market/INDEX/candles?range=1Y").get(0).get("close").asLong()).isBetween(5_000L, 20_000L);
        var idx = get("trader1", "/api/v1/market/INDEX/candles?range=1M");
        assertThat(idx.get(idx.size() - 1).get("close").asLong()).isEqualTo(10_000);
        assertThat(call("trader1", HttpMethod.GET, "/api/v1/market/SBNK/candles?range=7Y", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call("trader1", HttpMethod.GET, "/api/v1/market/NOPE/candles", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test @Order(3) void compareStartsAtZeroForEverySymbol() {
        var series = get("trader1", "/api/v1/market/compare?symbols=SBNK,STEL,INDEX&range=3M");
        assertThat(series).hasSize(3);
        for (JsonNode s : series) assertThat(s.get("points").get(0).get(1).asLong()).isZero();
        assertThat(call("trader1", HttpMethod.GET, "/api/v1/market/compare?symbols=A,B,C,D,E,F,G&range=1Y", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test @Order(4) void analyticsAreInternallyConsistent() {
        var rows = get("trader1", "/api/v1/analytics");
        assertThat(rows).hasSize(12);
        for (JsonNode r : rows) {
            assertThat(r.get("equityMills").asLong()).isEqualTo(r.get("assetsMills").asLong() - r.get("liabilitiesMills").asLong());
            if (!r.get("peX1000").isNull() && r.get("epsMills").asLong() != 0)
                assertThat(Math.signum(r.get("peX1000").asLong())).isEqualTo(Math.signum(r.get("epsMills").asLong()));
        }
    }

    @Test @Order(5) void newsAndDisclosuresFilterBySymbol() {
        assertThat(get("trader1", "/api/v1/news?kind=NEWS").size()).isGreaterThanOrEqualTo(4);
        var d = get("trader1", "/api/v1/news?kind=DISCLOSURE&symbol=SBNK");
        assertThat(d.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode n : d) assertThat(n.get("symbol").asText()).isEqualTo("SBNK");
    }

    @Test @Order(6) void favoritesArePerUserAndIdempotent() {
        call("trader1", HttpMethod.PUT, "/api/v1/me/favorites/SBNK", null);
        call("trader1", HttpMethod.PUT, "/api/v1/me/favorites/SBNK", null);
        assertThat(get("trader1", "/api/v1/me/favorites")).hasSize(1);
        assertThat(get("trader2", "/api/v1/me/favorites")).isEmpty();
        assertThat(call("trader1", HttpMethod.PUT, "/api/v1/me/favorites/NOPE", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        call("trader1", HttpMethod.DELETE, "/api/v1/me/favorites/SBNK", null);
        assertThat(get("trader1", "/api/v1/me/favorites")).isEmpty();
    }

    @Test @Order(7) void aTradeThroughTheTargetFiresTheAlertOnce() {
        var above = call("trader1", HttpMethod.POST, "/api/v1/alerts", Map.of("symbol", "SBNK", "direction", "ABOVE", "priceMills", 12_600)).getBody();
        call("trader1", HttpMethod.POST, "/api/v1/alerts", Map.of("symbol", "SBNK", "direction", "BELOW", "priceMills", 12_000));
        call("trader2", HttpMethod.POST, "/api/v1/orders", order("a-s1", "SELL", 100, 12_700));
        call("trader1", HttpMethod.POST, "/api/v1/orders", order("a-b1", "BUY", 100, 12_700));
        var list = get("trader1", "/api/v1/alerts");
        var fired = new ArrayList<JsonNode>(); list.forEach(fired::add);
        assertThat(fired.stream().filter(a -> a.get("status").asText().equals("TRIGGERED")).toList()).hasSize(1);
        assertThat(fired.stream().filter(a -> a.get("status").asText().equals("ACTIVE")).toList()).hasSize(1);
        assertThat(fired.stream().filter(a -> a.get("id").asLong() == above.get("id").asLong()).findFirst().orElseThrow().get("triggeredPriceMills").asLong()).isEqualTo(12_700);
        assertThat(call("trader2", HttpMethod.DELETE, "/api/v1/alerts/" + above.get("id").asLong(), null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);   // not theirs
        assertThat(call("trader1", HttpMethod.POST, "/api/v1/alerts", Map.of("symbol", "SBNK", "direction", "SIDEWAYS", "priceMills", 1)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test @Order(8) void liquidityProviderQuotesShowUpInMembersAndActivity() {
        call("lp1", HttpMethod.POST, "/api/v1/quotes", Map.of("quoteId", "lq-1", "symbol", "SBNK", "quantity", 1_000, "bidPriceMills", 12_400, "askPriceMills", 12_600));
        call("trader1", HttpMethod.POST, "/api/v1/orders", order("lp-hit", "BUY", 500, 12_600));
        var activity = get("trader1", "/api/v1/liquidity/SBNK/activity");
        var kinds = new HashSet<String>(); activity.forEach(a -> kinds.add(a.get("kind").asText()));
        assertThat(kinds).contains("LP_SELL");
        var members = get("trader1", "/api/v1/liquidity/members");
        var names = new ArrayList<String>(); members.forEach(m -> names.add(m.get("owner").asText()));
        assertThat(names).contains("lp1", "lp2");
        for (JsonNode m : members) if (m.get("owner").asText().equals("lp1")) { assertThat(m.get("orders").asLong()).isGreaterThanOrEqualTo(2); assertThat(m.get("trades").asLong()).isGreaterThanOrEqualTo(1); }
    }

    @Test @Order(9) void walletDepositIsIdempotentAndWithdrawalIsBounded() {
        long before = get("trader2", "/api/v1/wallet").get("cashMills").asLong();
        var dep = Map.of("clientRef", "d1", "direction", "DEPOSIT", "amountMills", 5_000_000, "bank", "Demo Bank A");
        var first = call("trader2", HttpMethod.POST, "/api/v1/wallet/transfers", dep).getBody();
        var again = call("trader2", HttpMethod.POST, "/api/v1/wallet/transfers", dep).getBody();
        assertThat(again.get("id").asLong()).isEqualTo(first.get("id").asLong());
        assertThat(get("trader2", "/api/v1/wallet").get("cashMills").asLong()).isEqualTo(before + 5_000_000);          // credited once
        assertThat(call("trader2", HttpMethod.POST, "/api/v1/wallet/transfers", Map.of("clientRef", "d1", "direction", "DEPOSIT", "amountMills", 6_000_000, "bank", "Demo Bank A")).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(call("trader2", HttpMethod.POST, "/api/v1/wallet/transfers", Map.of("clientRef", "w-big", "direction", "WITHDRAW", "amountMills", 999_000_000_000L, "bank", "Demo Bank D")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call("trader2", HttpMethod.POST, "/api/v1/wallet/transfers", Map.of("clientRef", "w-ok", "direction", "WITHDRAW", "amountMills", 1_000_000, "bank", "Demo Bank D")).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(call("trader2", HttpMethod.POST, "/api/v1/wallet/transfers", Map.of("clientRef", "w-x", "direction", "DEPOSIT", "amountMills", 1, "bank", "Nonexistent")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call("risk1", HttpMethod.GET, "/api/v1/wallet", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test @Order(10) void aMarginLoanBlocksWithdrawals() {
        call("trader2", HttpMethod.POST, "/api/v1/orders", order("m-s", "SELL", 20_000, 12_500));
        call("trader1", HttpMethod.POST, "/api/v1/orders", order("m-b", "BUY", 20_000, 12_500));
        assertThat(get("trader1", "/api/v1/accounts/me").get("loanMills").asLong()).isPositive();
        var r = call("trader1", HttpMethod.POST, "/api/v1/wallet/transfers", Map.of("clientRef", "w-loan", "direction", "WITHDRAW", "amountMills", 1, "bank", "Demo Bank D"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().get("detail").asText()).contains("margin loan");
    }

    @Test @Order(11) void publicApplicationsAreValidatedRateLimitedAndReviewedByAdmin() {
        var rt = new TestRestTemplate();
        String url = "http://localhost:" + port + "/api/v1/public/applications";
        assertThat(rt.postForEntity(url, Map.of("fullName", "X", "email", "not-an-email", "type", "INSIDE_QATAR"), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        for (int i = 0; i < 5; i++)
            assertThat(rt.postForEntity(url, Map.of("fullName", "Demo Person " + i, "email", "demo" + i + "@example.com", "type", "INSIDE_QATAR"), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(rt.postForEntity(url, Map.of("fullName", "One Too Many", "email", "x@example.com", "type", "AGENT"), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(get("admin", "/api/v1/admin/applications")).hasSize(5);
        assertThat(call("trader1", HttpMethod.GET, "/api/v1/admin/applications", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test @Order(12) void liveStreamPushesAMarketEventWhenAnOrderArrives() throws Exception {
        List<String> lines = new CopyOnWriteArrayList<>();
        var client = HttpClient.newHttpClient();
        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/stream")).header("Authorization", "Basic " + Base64.getEncoder().encodeToString("trader1:demo-pass".getBytes())).build();
        var future = client.sendAsync(req, HttpResponse.BodyHandlers.ofLines()).thenAccept(r -> r.body().forEach(lines::add));
        await().atMost(Duration.ofSeconds(5)).until(() -> lines.contains("event:hello"));
        call("trader2", HttpMethod.POST, "/api/v1/orders", order("sse-1", "SELL", 10, 12_800));
        await().atMost(Duration.ofSeconds(5)).until(() -> lines.contains("event:market"));
        assertThat(lines).contains("data:SBNK");
        assertThat(rest.getForEntity("/api/v1/stream", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        future.cancel(true);
    }
}
