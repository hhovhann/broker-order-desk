package dev.brokerdesk.repo;

import dev.brokerdesk.domain.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface OrderRepository extends JpaRepository<BrokerOrder, Long> {
    Optional<BrokerOrder> findByAccountIdAndClientOrderId(long accountId, String clientOrderId);
    List<BrokerOrder> findBySymbolAndStatusInOrderByIdAsc(String symbol, Collection<OrderStatus> statuses);
    List<BrokerOrder> findByAccountIdOrderByIdDesc(long accountId, Pageable page);
}
