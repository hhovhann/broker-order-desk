package dev.brokerdesk.domain;

import jakarta.persistence.*;

/** A listed security. Prices are in mills (1/1000 QAR). Synthetic reference data. */
@Entity @Table(name = "instrument")
public class Instrument {
    @Id private String symbol;
    private String name;
    @Column(name = "reference_price_mills") private long referencePriceMills;
    @Column(name = "last_price_mills") private long lastPriceMills;

    protected Instrument() {}
    public Instrument(String symbol, String name, long referencePriceMills) {
        this.symbol = symbol; this.name = name; this.referencePriceMills = referencePriceMills; this.lastPriceMills = referencePriceMills;
    }
    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public long getReferencePriceMills() { return referencePriceMills; }
    public long getLastPriceMills() { return lastPriceMills; }
    public void traded(long priceMills) { this.lastPriceMills = priceMills; }
}
