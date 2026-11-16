package dev.brokerdesk.service;

import dev.brokerdesk.domain.*;

public record OrderView(long orderId, String clientOrderId, String symbol, Side side, long quantity, long limitPriceMills,
                        long filledQty, long avgPriceMills, OrderStatus status, RejectReason rejectReason) {
    public static OrderView of(BrokerOrder o) {
        return new OrderView(o.getId(), o.getClientOrderId(), o.getSymbol(), o.getSide(), o.getQuantity(), o.getLimitPriceMills(),
                o.getFilledQty(), o.avgPriceMills(), o.getStatus(), o.getRejectReason());
    }
}
