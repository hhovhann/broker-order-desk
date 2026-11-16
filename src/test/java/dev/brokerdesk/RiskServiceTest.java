package dev.brokerdesk;

import dev.brokerdesk.config.BrokerProperties;
import dev.brokerdesk.domain.*;
import dev.brokerdesk.repo.*;
import dev.brokerdesk.service.RiskService;
import dev.brokerdesk.service.RiskService.Decision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskServiceTest {
    @Mock AccountRepository accounts;
    @Mock HoldingRepository holdings;
    @Mock InstrumentRepository instruments;
    RiskService risk;
    Account account = new Account("t", 10_000_000, 5_000_000);   // buying power 15,000,000 mills

    @BeforeEach void setUp() {
        risk = new RiskService(accounts, holdings, instruments, new BrokerProperties(10, 1_000_000_000, 35, "x"));
    }

    private void symbolAt12_500() { when(instruments.findById("SBNK")).thenReturn(Optional.of(new Instrument("SBNK", "n", 12_500))); }

    @Test void unknownSymbolIsRejectedBeforeAnyLocking() {
        when(instruments.findById("NOPE")).thenReturn(Optional.empty());
        assertThat(risk.checkAndReserve(1, "NOPE", Side.BUY, 10, 1000)).isEqualTo(new Decision.Rejected(RejectReason.UNKNOWN_SYMBOL));
        verifyNoInteractions(accounts);
    }

    @Test void priceOutsideTheDailyBandIsRejected() {
        symbolAt12_500();
        assertThat(risk.checkAndReserve(1, "SBNK", Side.BUY, 1, 11_249)).isEqualTo(new Decision.Rejected(RejectReason.PRICE_OUTSIDE_BAND));
        assertThat(risk.checkAndReserve(1, "SBNK", Side.BUY, 1, 13_751)).isEqualTo(new Decision.Rejected(RejectReason.PRICE_OUTSIDE_BAND));
        verifyNoInteractions(accounts);
    }

    @Test void buyReservesBuyingPowerWhenItFits() {
        symbolAt12_500();
        when(accounts.lockById(1)).thenReturn(Optional.of(account));
        var d = risk.checkAndReserve(1, "SBNK", Side.BUY, 1_000, 12_500);   // 12,500,000
        assertThat(d).isEqualTo(new Decision.Accepted(12_500_000));
        assertThat(account.buyingPower()).isEqualTo(2_500_000);
    }

    @Test void buyBeyondCashPlusMarginLineIsRejectedAndReservesNothing() {
        symbolAt12_500();
        when(accounts.lockById(1)).thenReturn(Optional.of(account));
        assertThat(risk.checkAndReserve(1, "SBNK", Side.BUY, 1_300, 12_500)).isEqualTo(new Decision.Rejected(RejectReason.INSUFFICIENT_BUYING_POWER));
        assertThat(account.getReservedMills()).isZero();
    }

    @Test void sellNeedsAvailableSharesAndReservesThem() {
        symbolAt12_500();
        when(accounts.lockById(1)).thenReturn(Optional.of(account));
        Holding h = new Holding(1, "SBNK", 100);
        when(holdings.findByAccountIdAndSymbol(1, "SBNK")).thenReturn(Optional.of(h));
        assertThat(risk.checkAndReserve(1, "SBNK", Side.SELL, 60, 12_500)).isInstanceOf(Decision.Accepted.class);
        assertThat(risk.checkAndReserve(1, "SBNK", Side.SELL, 60, 12_500)).isEqualTo(new Decision.Rejected(RejectReason.INSUFFICIENT_SHARES));   // only 40 left
        assertThat(h.available()).isEqualTo(40);
    }

    @Test void sellingAStockYouDoNotHoldIsRejected() {
        symbolAt12_500();
        when(accounts.lockById(1)).thenReturn(Optional.of(account));
        when(holdings.findByAccountIdAndSymbol(1, "SBNK")).thenReturn(Optional.empty());
        assertThat(risk.checkAndReserve(1, "SBNK", Side.SELL, 1, 12_500)).isEqualTo(new Decision.Rejected(RejectReason.INSUFFICIENT_SHARES));
    }

    @Test void orderValueCapIsEnforced() {
        symbolAt12_500();
        assertThat(risk.checkAndReserve(1, "SBNK", Side.BUY, 100_000, 12_500)).isEqualTo(new Decision.Rejected(RejectReason.MAX_ORDER_VALUE));
    }
}
