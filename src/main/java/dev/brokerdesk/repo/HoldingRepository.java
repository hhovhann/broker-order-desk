package dev.brokerdesk.repo;

import dev.brokerdesk.domain.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
    Optional<Holding> findByAccountIdAndSymbol(long accountId, String symbol);
    List<Holding> findByAccountId(long accountId);
}
