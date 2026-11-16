package dev.brokerdesk.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity @Table(name = "trade")
public class Trade {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String symbol;
    @Column(name = "price_mills") private long priceMills;
    private long quantity;
    @Column(name = "buy_order_id") private long buyOrderId;
    @Column(name = "sell_order_id") private long sellOrderId;
    @Column(name = "buy_account_id") private long buyAccountId;
    @Column(name = "sell_account_id") private long sellAccountId;
    @Column(name = "executed_at") private LocalDateTime executedAt = LocalDateTime.now(ZoneOffset.UTC);

    protected Trade() {}
    public Trade(String symbol, long priceMills, long quantity, long buyOrderId, long sellOrderId, long buyAccountId, long sellAccountId) {
        this.symbol = symbol; this.priceMills = priceMills; this.quantity = quantity; this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId; this.buyAccountId = buyAccountId; this.sellAccountId = sellAccountId;
    }
    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public long getPriceMills() { return priceMills; }
    public long getQuantity() { return quantity; }
    public long getBuyOrderId() { return buyOrderId; }
    public long getSellOrderId() { return sellOrderId; }
    public long getBuyAccountId() { return buyAccountId; }
    public long getSellAccountId() { return sellAccountId; }
    public LocalDateTime getExecutedAt() { return executedAt; }
}
