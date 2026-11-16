package dev.brokerdesk.web;

import dev.brokerdesk.repo.AccountRepository;
import dev.brokerdesk.service.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Maps the authenticated user to their account. Also used in @PreAuthorize expressions for ownership checks. */
@Component("currentAccount")
public class CurrentAccount {
    private final AccountRepository accounts;
    public CurrentAccount(AccountRepository accounts) { this.accounts = accounts; }

    public long idOf(Authentication auth) {
        return accounts.findByOwner(auth.getName()).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "user has no trading account")).getId();
    }

    public boolean owns(Authentication auth, long accountId) {
        return accounts.findByOwner(auth.getName()).map(a -> a.getId() == accountId).orElse(false);
    }
}
