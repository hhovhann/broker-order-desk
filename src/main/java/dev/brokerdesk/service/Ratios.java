package dev.brokerdesk.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fundamental ratios, computed from stored statement figures and the last price. Money in mills (1/1000 QAR).
 * Ratios come back scaled by 1000 (P/E 11.134 is 11134); dividend yield in basis points. Null means "not meaningful" (zero denominator).
 */
public final class Ratios {
    private Ratios() {}

    public record Input(long sharesOutstanding, long assetsMills, long liabilitiesMills, long profitMills, long dividendPerShareMills, long lastPriceMills) {}
    public record Result(long equityMills, Long epsMills, Long bookValueMills, Long peX1000, Long pbX1000, Long dividendYieldBps, Long profitOverEquityBps) {}

    public static Result compute(Input in) {
        long equity = in.assetsMills() - in.liabilitiesMills();
        Long eps = in.sharesOutstanding() == 0 ? null : div(in.profitMills(), in.sharesOutstanding(), 1);
        Long book = in.sharesOutstanding() == 0 ? null : div(equity, in.sharesOutstanding(), 1);
        Long pe = eps == null || eps == 0 ? null : div(in.lastPriceMills(), eps, 1000);
        Long pb = book == null || book == 0 ? null : div(in.lastPriceMills(), book, 1000);
        Long yield = in.lastPriceMills() == 0 ? null : div(in.dividendPerShareMills(), in.lastPriceMills(), 10_000);
        Long roe = equity == 0 ? null : div(in.profitMills(), equity, 10_000);
        return new Result(equity, eps, book, pe, pb, yield, roe);
    }

    /** (a / b) * scale, rounded half-even, without overflowing long. */
    private static long div(long a, long b, long scale) {
        return BigDecimal.valueOf(a).multiply(BigDecimal.valueOf(scale)).divide(BigDecimal.valueOf(b), 0, RoundingMode.HALF_EVEN).longValueExact();
    }
}
