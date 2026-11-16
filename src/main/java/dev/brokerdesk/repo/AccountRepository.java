package dev.brokerdesk.repo;

import dev.brokerdesk.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByOwner(String owner);
    List<Account> findByLiquidityProviderTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> lockById(@Param("id") long id);

    /** Locks in id order, always, so two settlements can never deadlock on the same pair of accounts. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id")
    List<Account> lockAll(@Param("ids") Collection<Long> ids);
}
