package dev.brokerdesk.web;

import dev.brokerdesk.domain.Side;
import dev.brokerdesk.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/** Liquidity provider two-sided quote: a bid and an offer placed together through the same risk, engine and settlement path as any order. */
@RestController
@RequestMapping("/api/v1/quotes")
@PreAuthorize("hasRole('LIQUIDITY_PROVIDER')")
public class QuoteController {
    public record QuoteRequest(@NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9._-]+") String quoteId,
                               @NotBlank @Pattern(regexp = "[A-Z0-9]{2,12}") String symbol,
                               @Positive @Max(100_000_000) long quantity,
                               @Positive @Max(1_000_000_000) long bidPriceMills,
                               @Positive @Max(1_000_000_000) long askPriceMills) {}
    public record QuoteResult(OrderView bid, OrderView ask) {}

    private final OrderService orders;
    private final CurrentAccount me;

    public QuoteController(OrderService orders, CurrentAccount me) { this.orders = orders; this.me = me; }

    @PostMapping
    CompletableFuture<QuoteResult> quote(@Valid @RequestBody QuoteRequest q, Authentication auth) {
        if (q.bidPriceMills() >= q.askPriceMills()) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "bid must be below ask");
        long acct = me.idOf(auth);
        var bid = orders.place(acct, new OrderService.PlaceCommand(q.quoteId() + "-B", q.symbol(), Side.BUY, q.quantity(), q.bidPriceMills()));
        var ask = orders.place(acct, new OrderService.PlaceCommand(q.quoteId() + "-S", q.symbol(), Side.SELL, q.quantity(), q.askPriceMills()));
        return bid.thenCombine(ask, QuoteResult::new);
    }
}
