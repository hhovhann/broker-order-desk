package dev.brokerdesk.domain;

import java.util.List;

public enum OrderStatus {
    NEW, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED;

    public static final List<OrderStatus> OPEN = List.of(NEW, PARTIALLY_FILLED);
    public boolean isOpen() { return this == NEW || this == PARTIALLY_FILLED; }
}
