package dev.brokerdesk.web;

import dev.brokerdesk.domain.Side;
import dev.brokerdesk.repo.AccountRepository;
import dev.brokerdesk.repo.TradeRepository;
import dev.brokerdesk.service.MarginService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
    public record Me(String username, List<String> roles, Long accountId) {}
    public record StatementLine(long tradeId, String executedAt, String symbol, Side side, long quantity, long priceMills, long valueMills) {}

    private final MarginService margin;
    private final CurrentAccount me;
    private final AccountRepository accounts;
    private final TradeRepository trades;

    public AccountController(MarginService margin, CurrentAccount me, AccountRepository accounts, TradeRepository trades) {
        this.margin = margin; this.me = me; this.accounts = accounts; this.trades = trades;
    }

    /** Who am I: lets a client choose which screens to show. The server still enforces every role on every call. */
    @GetMapping("/me")
    Me whoAmI(Authentication auth) {
        List<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).map(a -> a.replace("ROLE_", "")).sorted().toList();
        return new Me(auth.getName(), roles, accounts.findByOwner(auth.getName()).map(a -> a.getId()).orElse(null));
    }

    @GetMapping("/accounts/me")
    @PreAuthorize("hasAnyRole('TRADER','LIQUIDITY_PROVIDER')")
    MarginService.Report mine(Authentication auth) { return margin.report(me.idOf(auth)); }

    /** Trade statement: every execution of the caller's account, newest first. */
    @GetMapping("/accounts/me/trades")
    @PreAuthorize("hasAnyRole('TRADER','LIQUIDITY_PROVIDER')")
    List<StatementLine> statement(Authentication auth) {
        long id = me.idOf(auth);
        return trades.findTop50ByBuyAccountIdOrSellAccountIdOrderByIdDesc(id, id).stream().map(t -> {
            Side side = t.getBuyAccountId() == id ? Side.BUY : Side.SELL;
            return new StatementLine(t.getId(), t.getExecutedAt().toString(), t.getSymbol(), side, t.getQuantity(), t.getPriceMills(), Math.multiplyExact(t.getPriceMills(), t.getQuantity()));
        }).toList();
    }

    /** Risk officers see any account; a trader only their own. */
    @GetMapping("/accounts/{id}/margin")
    @PreAuthorize("hasRole('RISK') or @currentAccount.owns(authentication, #id)")
    MarginService.Report margin(@PathVariable long id) { return margin.report(id); }

    @GetMapping("/risk/margin-calls")
    @PreAuthorize("hasRole('RISK')")
    List<MarginService.Report> marginCalls() { return margin.marginCalls(); }
}
