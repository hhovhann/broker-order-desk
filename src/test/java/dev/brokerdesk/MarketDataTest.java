package dev.brokerdesk;

import com.fasterxml.jackson.databind.JsonNode;
import dev.brokerdesk.domain.*;
import dev.brokerdesk.repo.*;
import dev.brokerdesk.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** Price-board statistics, the account statement and the admin audit trail, over real HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarketDataTest {
    @Autowired TestRestTemplate rest;
    @Autowired OrderService orders;
    @Autowired AccountRepository accounts;
    @Autowired HoldingRepository holdings;
    @Autowired InstrumentRepository instruments;

    @Test void boardStatementAndAuditReflectTheTrades() {
        instruments.save(new Instrument("BOARD", "Board Test", 10_000));
        Account seller = accounts.save(new Account("trader1", 0, 0));
        Account buyer = accounts.save(new Account("trader2", 100_000_000, 0));
        holdings.save(new Holding(seller.getId(), "BOARD", 1_000));
        orders.place(seller.getId(), new OrderService.PlaceCommand("s1", "BOARD", Side.SELL, 300, 10_000)).join();
        orders.place(seller.getId(), new OrderService.PlaceCommand("s2", "BOARD", Side.SELL, 200, 10_100)).join();
        orders.place(seller.getId(), new OrderService.PlaceCommand("s3", "BOARD", Side.SELL, 100, 10_300)).join();
        orders.place(buyer.getId(), new OrderService.PlaceCommand("b1", "BOARD", Side.BUY, 500, 10_100)).join();   // 300 @ 10,000 and 200 @ 10,100
        orders.place(buyer.getId(), new OrderService.PlaceCommand("b2", "BOARD", Side.BUY, 50, 9_900)).join();     // rests as the best bid

        JsonNode row = null;
        for (JsonNode r : rest.withBasicAuth("risk1", "demo-pass").getForObject("/api/v1/market/board", JsonNode.class))
            if (r.get("symbol").asText().equals("BOARD")) row = r;
        assertThat(row).isNotNull();
        assertThat(row.get("trades").asLong()).isEqualTo(2);
        assertThat(row.get("shares").asLong()).isEqualTo(500);
        assertThat(row.get("valueMills").asLong()).isEqualTo(5_020_000);   // 300*10,000 + 200*10,100
        assertThat(row.get("averageMills").asLong()).isEqualTo(10_040);
        assertThat(row.get("highMills").asLong()).isEqualTo(10_100);
        assertThat(row.get("lowMills").asLong()).isEqualTo(10_000);
        assertThat(row.get("lastPriceMills").asLong()).isEqualTo(10_100);
        assertThat(row.get("changeBps").asLong()).isEqualTo(100);           // +1.00% against the 10,000 reference
        assertThat(row.get("offerPriceMills").asLong()).isEqualTo(10_300);
        assertThat(row.get("offerShares").asLong()).isEqualTo(100);
        assertThat(row.get("bidPriceMills").asLong()).isEqualTo(9_900);
        assertThat(row.get("spark")).hasSize(2);

        var statement = rest.withBasicAuth("trader2", "demo-pass").getForObject("/api/v1/accounts/me/trades", JsonNode.class);
        assertThat(statement).hasSize(2);
        assertThat(statement.get(0).get("side").asText()).isEqualTo("BUY");

        var audit = rest.withBasicAuth("admin", "demo-pass").getForObject("/api/v1/admin/audit", JsonNode.class);
        assertThat(audit.toString()).contains("ORDER_ACCEPTED").contains("FILL");
        assertThat(rest.withBasicAuth("trader1", "demo-pass").getForEntity("/api/v1/admin/audit", String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var me = rest.withBasicAuth("lp1", "demo-pass").getForObject("/api/v1/me", JsonNode.class);
        assertThat(me.get("roles").toString()).contains("LIQUIDITY_PROVIDER");
    }
}
