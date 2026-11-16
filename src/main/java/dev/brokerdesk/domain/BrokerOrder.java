package dev.brokerdesk.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity @Table(name = "broker_order")
public class BrokerOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "account_id") private long accountId;
    @Column(name = "client_order_id") private String clientOrderId;
    private String symbol;
    @Enumerated(EnumType.STRING) private Side side;
    private long quantity;
    @Column(name = "limit_price_mills") private long limitPriceMills;
    @Column(name = "filled_qty") private long filledQty;
    @Column(name = "filled_notional_mills") private long filledNotionalMills;
    @Column(name = "reserved_mills") private long reservedMills;
    @Enumerated(EnumType.STRING) private OrderStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "reject_reason") private RejectReason rejectReason;
    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);

    protected BrokerOrder() {}
    public BrokerOrder(long accountId, String clientOrderId, String symbol, Side side, long quantity, long limitPriceMills) {
        this.accountId = accountId; this.clientOrderId = clientOrderId; this.symbol = symbol; this.side = side;
        this.quantity = quantity; this.limitPriceMills = limitPriceMills; this.status = OrderStatus.NEW;
    }

    public Long getId() { return id; }
    public long getAccountId() { return accountId; }
    public String getClientOrderId() { return clientOrderId; }
    public String getSymbol() { return symbol; }
    public Side getSide() { return side; }
    public long getQuantity() { return quantity; }
    public long getLimitPriceMills() { return limitPriceMills; }
    public long getFilledQty() { return filledQty; }
    public long getFilledNotionalMills() { return filledNotionalMills; }
    public long getReservedMills() { return reservedMills; }
    public OrderStatus getStatus() { return status; }
    public RejectReason getRejectReason() { return rejectReason; }
    public long remaining() { return quantity - filledQty; }
    public long avgPriceMills() { return filledQty == 0 ? 0 : filledNotionalMills / filledQty; }

    public void reservedBuy(long mills) { this.reservedMills = mills; }

    public void rejected(RejectReason r) { this.status = OrderStatus.REJECTED; this.rejectReason = r; }

    /** Applies one fill. For buys, releases the reservation made at the limit price (the fill may be cheaper). */
    public long applyFill(long qty, long priceMills) {
        if (qty > remaining()) throw new IllegalStateException("fill exceeds remaining");
        filledQty += qty;
        filledNotionalMills = Math.addExact(filledNotionalMills, Math.multiplyExact(priceMills, qty));
        status = filledQty == quantity ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
        if (side == Side.BUY) {
            long release = Math.multiplyExact(limitPriceMills, qty);
            reservedMills -= release;
            return release;
        }
        return 0;
    }

    /** Cancels what is left. Returns the buy reservation to hand back (zero for sells; the caller releases shares). */
    public long cancel(RejectReason reason) {
        long toRelease = side == Side.BUY ? reservedMills : 0;
        reservedMills = 0;
        status = OrderStatus.CANCELLED;
        this.rejectReason = reason;
        return toRelease;
    }
}
