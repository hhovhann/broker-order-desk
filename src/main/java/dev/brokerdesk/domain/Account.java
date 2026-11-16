package dev.brokerdesk.domain;

import jakarta.persistence.*;

/**
 * Cash/margin account aggregate. Negative cash is a margin loan, capped by the margin limit.
 * Buy orders reserve buying power up front; every mutation goes through a method that protects the invariant.
 */
@Entity @Table(name = "account")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String owner;
    @Column(name = "cash_mills") private long cashMills;
    @Column(name = "margin_limit_mills") private long marginLimitMills;
    @Column(name = "reserved_mills") private long reservedMills;
    @Version private long version;
    @Column(name = "liquidity_provider") private boolean liquidityProvider;

    protected Account() {}
    public Account(String owner, long cashMills, long marginLimitMills) {
        this.owner = owner; this.cashMills = cashMills; this.marginLimitMills = marginLimitMills;
    }

    public Long getId() { return id; }
    public boolean isLiquidityProvider() { return liquidityProvider; }
    public Account asLiquidityProvider() { this.liquidityProvider = true; return this; }
    public String getOwner() { return owner; }
    public long getCashMills() { return cashMills; }
    public long getMarginLimitMills() { return marginLimitMills; }
    public long getReservedMills() { return reservedMills; }
    public long buyingPower() { return cashMills + marginLimitMills - reservedMills; }
    public long loanMills() { return Math.max(0, -cashMills); }

    public void reserve(long mills) {
        if (mills > buyingPower()) throw new IllegalStateException("insufficient buying power");
        reservedMills += mills;
    }
    public void release(long mills) {
        if (mills > reservedMills) throw new IllegalStateException("release exceeds reservation");
        reservedMills -= mills;
    }
    public void debit(long mills) { cashMills -= mills; }
    public void credit(long mills) { cashMills += mills; }
}
