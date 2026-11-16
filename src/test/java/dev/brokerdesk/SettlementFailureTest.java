package dev.brokerdesk;

import dev.brokerdesk.domain.*;
import dev.brokerdesk.engine.OrderBook;
import dev.brokerdesk.repo.*;
import dev.brokerdesk.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

/** If the database write fails after an in-memory match, the order is rejected, its reservation returned, and the book rebuilt from the database. */
@SpringBootTest
class SettlementFailureTest {
    @MockitoSpyBean SettlementService settlement;
    @Autowired OrderService orders;
    @Autowired AccountRepository accounts;
    @Autowired HoldingRepository holdings;
    @Autowired InstrumentRepository instruments;
    @Autowired OrderRepository orderRepo;
    @Autowired dev.brokerdesk.engine.MatchingEngine engine;

    @Test void failedSettlementRejectsTheOrderReleasesReservationAndRestoresTheBook() {
        instruments.save(new Instrument("FAIL", "fail", 10_000));
        Account seller = accounts.save(new Account("fail-s", 0, 0));
        Account buyer = accounts.save(new Account("fail-b", 10_000_000, 0));
        holdings.save(new Holding(seller.getId(), "FAIL", 100));
        orders.place(seller.getId(), new OrderService.PlaceCommand("s1", "FAIL", Side.SELL, 100, 10_000)).join();

        doThrow(new RuntimeException("database went away")).doCallRealMethod().when(settlement).applyMatch(anyLong(), any());
        assertThatThrownBy(() -> orders.place(buyer.getId(), new OrderService.PlaceCommand("b1", "FAIL", Side.BUY, 100, 10_000)).join())
                .isInstanceOf(CompletionException.class);

        var failed = orderRepo.findByAccountIdAndClientOrderId(buyer.getId(), "b1").orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(failed.getRejectReason()).isEqualTo(RejectReason.SYSTEM_ERROR);
        assertThat(accounts.findById(buyer.getId()).orElseThrow().getReservedMills()).isZero();
        assertThat(accounts.findById(buyer.getId()).orElseThrow().getCashMills()).isEqualTo(10_000_000);
        assertThat(engine.depth("FAIL", 5).join().asks()).singleElement().extracting(OrderBook.Level::quantity).isEqualTo(100L);   // the maker is whole again

        // and the desk keeps working: the same buy now succeeds
        var retry = orders.place(buyer.getId(), new OrderService.PlaceCommand("b2", "FAIL", Side.BUY, 100, 10_000)).join();
        assertThat(retry.status()).isEqualTo(OrderStatus.FILLED);
    }
}
