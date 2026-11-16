package dev.brokerdesk.web;

import dev.brokerdesk.domain.Side;
import jakarta.validation.constraints.*;

/** Prices are integers in mills (1/1000 QAR): 12.500 QAR is 12500. No floating point anywhere near money. */
public record PlaceOrderRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9._-]+") String clientOrderId,
        @NotBlank @Pattern(regexp = "[A-Z0-9]{2,12}") String symbol,
        @NotNull Side side,
        @Positive @Max(100_000_000) long quantity,
        @Positive @Max(1_000_000_000) long limitPriceMills) {}
