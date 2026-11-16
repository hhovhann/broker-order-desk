package dev.brokerdesk.web;

import dev.brokerdesk.service.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/orders")
@PreAuthorize("hasAnyRole('TRADER','LIQUIDITY_PROVIDER')")
public class OrderController {
    private final OrderService orders;
    private final CurrentAccount me;

    public OrderController(OrderService orders, CurrentAccount me) { this.orders = orders; this.me = me; }

    /** 201 with the order outcome. A risk rejection is a business outcome (status REJECTED + rejectReason), not an HTTP error. */
    @PostMapping
    CompletableFuture<ResponseEntity<OrderView>> place(@Valid @RequestBody PlaceOrderRequest r, Authentication auth) {
        return orders.place(me.idOf(auth), new OrderService.PlaceCommand(r.clientOrderId(), r.symbol(), r.side(), r.quantity(), r.limitPriceMills()))
                .thenApply(v -> ResponseEntity.status(HttpStatus.CREATED).body(v));
    }

    @DeleteMapping("/{id}")
    CompletableFuture<OrderView> cancel(@PathVariable long id, Authentication auth) { return orders.cancel(me.idOf(auth), id); }

    @GetMapping("/{id}")
    OrderView get(@PathVariable long id, Authentication auth) { return orders.get(me.idOf(auth), id); }

    @GetMapping
    List<OrderView> mine(Authentication auth) { return orders.recent(me.idOf(auth)); }
}
