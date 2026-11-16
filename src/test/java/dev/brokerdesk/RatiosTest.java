package dev.brokerdesk;

import dev.brokerdesk.service.MarketSummaryService;
import dev.brokerdesk.service.Ratios;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

class RatiosTest {
    @Test void knownFigures() {
        // 1,000,000 shares, profit 250,000 QAR, equity 2,000,000 QAR, price 2.500, dividend 0.100
        var r = Ratios.compute(new Ratios.Input(1_000_000, 3_000_000_000L, 1_000_000_000L, 250_000_000L, 100, 2_500));
        assertThat(r.equityMills()).isEqualTo(2_000_000_000L);
        assertThat(r.epsMills()).isEqualTo(250);              // 0.250
        assertThat(r.bookValueMills()).isEqualTo(2_000);      // 2.000
        assertThat(r.peX1000()).isEqualTo(10_000);            // 10.000
        assertThat(r.pbX1000()).isEqualTo(1_250);             // 1.250
        assertThat(r.dividendYieldBps()).isEqualTo(400);      // 4.00%
        assertThat(r.profitOverEquityBps()).isEqualTo(1_250); // 12.50%
    }

    @Test void lossGivesNegativePeAndZeroProfitGivesNone() {
        var loss = Ratios.compute(new Ratios.Input(1_000_000, 3_000_000_000L, 1_000_000_000L, -100_000_000L, 0, 2_500));
        assertThat(loss.peX1000()).isEqualTo(-25_000);
        var zero = Ratios.compute(new Ratios.Input(1_000_000, 3_000_000_000L, 1_000_000_000L, 0, 0, 2_500));
        assertThat(zero.peX1000()).isNull();
        assertThat(zero.dividendYieldBps()).isZero();
    }

    @Test void zeroDenominatorsAreNotMeaningfulRatherThanExceptions() {
        var r = Ratios.compute(new Ratios.Input(0, 0, 0, 0, 0, 0));
        assertThat(r.epsMills()).isNull(); assertThat(r.pbX1000()).isNull(); assertThat(r.dividendYieldBps()).isNull(); assertThat(r.profitOverEquityBps()).isNull();
    }

    @Test void largeFiguresDoNotOverflow() {
        var r = Ratios.compute(new Ratios.Input(9_000_000_000L, 1_400_000_000_000_000L, 1_300_000_000_000_000L, 4_600_000_000_000L, 800, 18_300));
        assertThat(r.pbX1000()).isNotNull();
    }

    @Test void sessionIsOpenSundayToThursdayMorningInDoha() {
        var svc = new MarketSummaryService(null, null, Clock.systemUTC());
        ZoneId doha = ZoneId.of("Asia/Qatar");
        assertThat(svc.sessionState(ZonedDateTime.of(2026, 11, 15, 10, 0, 0, 0, doha))).isEqualTo("OPEN");     // Sunday
        assertThat(svc.sessionState(ZonedDateTime.of(2026, 11, 19, 12, 59, 0, 0, doha))).isEqualTo("OPEN");    // Thursday
        assertThat(svc.sessionState(ZonedDateTime.of(2026, 11, 19, 13, 0, 0, 0, doha))).isEqualTo("CLOSED");
        assertThat(svc.sessionState(ZonedDateTime.of(2026, 11, 20, 10, 0, 0, 0, doha))).isEqualTo("CLOSED");   // Friday
        assertThat(svc.sessionState(ZonedDateTime.of(2026, 11, 15, 9, 29, 0, 0, doha))).isEqualTo("CLOSED");
    }
}
