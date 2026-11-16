package dev.brokerdesk.domain;

import jakarta.persistence.*;

/** Shares of one symbol held by one account. Sell orders reserve shares, so no short selling and no double selling. */
@Entity @Table(name = "holding")
public class Holding {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "account_id") private long accountId;
    private String symbol;
    private long quantity;
    @Column(name = "reserved_qty") private long reservedQty;

    protected Holding() {}
    public Holding(long accountId, String symbol, long quantity) { this.accountId = accountId; this.symbol = symbol; this.quantity = quantity; }

    public long getAccountId() { return accountId; }
    public String getSymbol() { return symbol; }
    public long getQuantity() { return quantity; }
    public long getReservedQty() { return reservedQty; }
    public long available() { return quantity - reservedQty; }

    public void reserve(long qty) {
        if (qty > available()) throw new IllegalStateException("insufficient shares");
        reservedQty += qty;
    }
    public void release(long qty) {
        if (qty > reservedQty) throw new IllegalStateException("release exceeds reservation");
        reservedQty -= qty;
    }
    public void add(long qty) { quantity += qty; }
    /** A reserved share leaves the account when its sell order fills. */
    public void deliver(long qty) { release(qty); quantity -= qty; }
}
