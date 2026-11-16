package dev.brokerdesk;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The whole story over real HTTP with real security, in the same order as the demo video. Exact numbers are asserted. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.profiles.active=demo")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BrokerApiTest {
    @Autowired TestRestTemplate rest;
    static long t1Account, t2Account, sellRestingId, firstBuyId;

    JsonNode call(String user, HttpMethod m, String url, Object body) {
        var r = rest.withBasicAuth(user, "demo-pass").exchange(url, m, body == null ? null : new HttpEntity<>(body), JsonNode.class);
        return r.getBody();
    }
    HttpStatusCode status(String user, HttpMethod m, String url, Object body) {
        return rest.withBasicAuth(user, "demo-pass").exchange(url, m, body == null ? null : new HttpEntity<>(body), JsonNode.class).getStatusCode();
    }
    Map<String, Object> order(String cid, String side, long qty, long price) {
        return Map.of("clientOrderId", cid, "symbol", "SBNK", "side", side, "quantity", qty, "limitPriceMills", price);
    }

    @Test @Order(1) void securityRejectsAnonymousAndWrongRoleAndBadInput() {
        assertThat(rest.getForEntity("/api/v1/orders", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.withBasicAuth("trader1", "wrong").getForEntity("/api/v1/orders", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(status("risk1", HttpMethod.POST, "/api/v1/orders", order("x1", "BUY", 1, 12_500))).isEqualTo(HttpStatus.FORBIDDEN);   // risk officers do not trade
        var bad = rest.withBasicAuth("trader1", "demo-pass").exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(Map.of("clientOrderId", "bad", "symbol", "qnbk", "side", "BUY", "quantity", -5, "limitPriceMills", 0)), JsonNode.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bad.getBody().get("detail").asText()).contains("quantity").contains("limitPriceMills").contains("symbol");
        t1Account = call("trader1", HttpMethod.GET, "/api/v1/accounts/me", null).get("accountId").asLong();
        t2Account = call("trader2", HttpMethod.GET, "/api/v1/accounts/me", null).get("accountId").asLong();
    }

    @Test @Order(2) void sellRestsInTheBook() {
        var o = call("trader2", HttpMethod.POST, "/api/v1/orders", order("s-1", "SELL", 20_000, 12_500));
        assertThat(o.get("status").asText()).isEqualTo("NEW");
        sellRestingId = o.get("orderId").asLong();
        var book = call("trader1", HttpMethod.GET, "/api/v1/market/SBNK/book", null);
        assertThat(book.get("asks").get(0).get("quantity").asLong()).isEqualTo(20_000);
    }

    @Test @Order(3) void buyFillsAndMarginAccountShowsLoan() {
        var o = call("trader1", HttpMethod.POST, "/api/v1/orders", order("b-1", "BUY", 20_000, 12_500));
        firstBuyId = o.get("orderId").asLong();
        assertThat(o.get("status").asText()).isEqualTo("FILLED");
        assertThat(o.get("avgPriceMills").asLong()).isEqualTo(12_500);
        var m = call("trader1", HttpMethod.GET, "/api/v1/accounts/me", null);
        assertThat(m.get("cashMills").asLong()).isEqualTo(-150_000_000L);
        assertThat(m.get("loanMills").asLong()).isEqualTo(150_000_000L);
        assertThat(m.get("equityBps").asLong()).isEqualTo(4_000);
        assertThat(m.get("marginCall").asBoolean()).isFalse();
        assertThat(call("trader2", HttpMethod.GET, "/api/v1/accounts/me", null).get("cashMills").asLong()).isEqualTo(300_000_000L);
    }

    @Test @Order(4) void aLowerPricedTradeMovesTheMarkAndRaisesAMarginCall() {
        call("trader2", HttpMethod.POST, "/api/v1/orders", order("s-2", "SELL", 5_000, 11_300));
        var o = call("trader1", HttpMethod.POST, "/api/v1/orders", order("b-2", "BUY", 1_000, 11_300));
        assertThat(o.get("status").asText()).isEqualTo("FILLED");
        var m = call("trader1", HttpMethod.GET, "/api/v1/accounts/me", null);
        assertThat(m.get("marketValueMills").asLong()).isEqualTo(237_300_000L);
        assertThat(m.get("equityBps").asLong()).isEqualTo(3_202);
        assertThat(m.get("marginCall").asBoolean()).isTrue();
    }

    @Test @Order(5) void onlyRiskSeesMarginCallsAndTradersCannotReadEachOther() {
        var calls = call("risk1", HttpMethod.GET, "/api/v1/risk/margin-calls", null);
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).get("owner").asText()).isEqualTo("trader1");
        assertThat(status("trader1", HttpMethod.GET, "/api/v1/risk/margin-calls", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status("trader1", HttpMethod.GET, "/api/v1/accounts/" + t2Account + "/margin", null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status("risk1", HttpMethod.GET, "/api/v1/accounts/" + t2Account + "/margin", null)).isEqualTo(HttpStatus.OK);
        assertThat(status("trader1", HttpMethod.GET, "/api/v1/accounts/" + t1Account + "/margin", null)).isEqualTo(HttpStatus.OK);
        assertThat(status("trader1", HttpMethod.GET, "/api/v1/orders/" + sellRestingId, null)).isEqualTo(HttpStatus.NOT_FOUND);   // someone else's order
    }

    @Test @Order(6) void riskRejectionsAreBusinessOutcomes() {
        assertThat(call("trader1", HttpMethod.POST, "/api/v1/orders", order("r-1", "BUY", 10, 20_000)).get("rejectReason").asText()).isEqualTo("PRICE_OUTSIDE_BAND");
        assertThat(call("trader1", HttpMethod.POST, "/api/v1/orders", order("r-2", "BUY", 30_000, 12_500)).get("rejectReason").asText()).isEqualTo("INSUFFICIENT_BUYING_POWER");
        assertThat(call("trader1", HttpMethod.POST, "/api/v1/orders", order("r-3", "SELL", 50_000, 12_500)).get("rejectReason").asText()).isEqualTo("INSUFFICIENT_SHARES");
    }

    @Test @Order(7) void resendingTheSameClientOrderIdIsIdempotent() {
        var again = call("trader1", HttpMethod.POST, "/api/v1/orders", order("b-1", "BUY", 20_000, 12_500));
        assertThat(again.get("orderId").asLong()).isEqualTo(firstBuyId);
        assertThat(again.get("status").asText()).isEqualTo("FILLED");
        assertThat(call("trader1", HttpMethod.GET, "/api/v1/accounts/me", null).get("cashMills").asLong()).isEqualTo(-161_300_000L);   // not charged twice
        assertThat(status("trader1", HttpMethod.POST, "/api/v1/orders", order("b-1", "BUY", 99, 12_500))).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test @Order(8) void cancelReleasesTheReservedShares() {
        var open = call("trader2", HttpMethod.GET, "/api/v1/orders", null);
        long restingId = 0;
        for (JsonNode o : open) if (o.get("clientOrderId").asText().equals("s-2")) restingId = o.get("orderId").asLong();
        var cancelled = call("trader2", HttpMethod.DELETE, "/api/v1/orders/" + restingId, null);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(status("trader2", HttpMethod.DELETE, "/api/v1/orders/" + restingId, null)).isEqualTo(HttpStatus.CONFLICT);   // already cancelled
        assertThat(call("trader1", HttpMethod.GET, "/api/v1/market/SBNK/book", null).get("asks")).isEmpty();
    }

    @Test @Order(9) void liquidityProviderQuotesBothSidesAndOnlyThatRoleMay() {
        var q = Map.of("quoteId", "q-1", "symbol", "SBNK", "quantity", 1_000, "bidPriceMills", 12_400, "askPriceMills", 12_600);
        assertThat(status("trader1", HttpMethod.POST, "/api/v1/quotes", q)).isEqualTo(HttpStatus.FORBIDDEN);
        var r = call("lp1", HttpMethod.POST, "/api/v1/quotes", q);
        assertThat(r.get("bid").get("status").asText()).isEqualTo("NEW");
        assertThat(r.get("ask").get("status").asText()).isEqualTo("NEW");
        var book = call("trader1", HttpMethod.GET, "/api/v1/market/SBNK/book", null);
        assertThat(book.get("bids").get(0).get("priceMills").asLong()).isEqualTo(12_400);
        assertThat(book.get("asks").get(0).get("priceMills").asLong()).isEqualTo(12_600);
        var crossed = Map.of("quoteId", "q-2", "symbol", "SBNK", "quantity", 10, "bidPriceMills", 12_600, "askPriceMills", 12_400);
        assertThat(status("lp1", HttpMethod.POST, "/api/v1/quotes", crossed)).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
