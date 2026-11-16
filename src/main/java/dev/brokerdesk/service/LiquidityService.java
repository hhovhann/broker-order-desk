package dev.brokerdesk.service;

import dev.brokerdesk.domain.Account;
import dev.brokerdesk.repo.AccountRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/** Liquidity-provider view: who provides liquidity, how much, and where liquidity-provider trades print against ordinary market trades. */
@Service
public class LiquidityService {
    public record Member(String owner, int quotedSymbols, long orders, long filledShares, long trades) {}
    public record Print(String time, long priceMills, long quantity, String kind) {}

    private final AccountRepository accounts;
    private final JdbcClient jdbc;
    public LiquidityService(AccountRepository accounts, JdbcClient jdbc) { this.accounts = accounts; this.jdbc = jdbc; }

    public List<Member> members() {
        List<Member> out = new ArrayList<>();
        for (Account a : accounts.findByLiquidityProviderTrue()) {
            long id = a.getId();
            long symbols = jdbc.sql("select count(distinct symbol) from broker_order where account_id = :a").param("a", id).query(Long.class).single();
            long orders = jdbc.sql("select count(*) from broker_order where account_id = :a").param("a", id).query(Long.class).single();
            long filled = jdbc.sql("select coalesce(sum(filled_qty), 0) from broker_order where account_id = :a").param("a", id).query(Long.class).single();
            long trades = jdbc.sql("select count(*) from trade where buy_account_id = :a or sell_account_id = :a").param("a", id).query(Long.class).single();
            out.add(new Member(a.getOwner(), (int) symbols, orders, filled, trades));
        }
        return out;
    }

    /** LP_BUY / LP_SELL: a liquidity provider was that side. MARKET_BUY / MARKET_SELL: ordinary trades; the taker side is shown. */
    public List<Print> activity(String symbol) {
        Set<Long> lp = accounts.findByLiquidityProviderTrue().stream().map(Account::getId).collect(Collectors.toSet());
        return jdbc.sql("select executed_at, price_mills, quantity, buy_account_id, sell_account_id, buy_order_id, sell_order_id from trade where symbol = :s order by id desc limit 300").param("s", symbol)
                .query((rs, i) -> {
                    long buyAcc = rs.getLong(4), sellAcc = rs.getLong(5);
                    String kind = lp.contains(buyAcc) ? "LP_BUY" : lp.contains(sellAcc) ? "LP_SELL" : rs.getLong(6) > rs.getLong(7) ? "MARKET_BUY" : "MARKET_SELL";   // later order id = the taker
                    return new Print(rs.getTimestamp(1).toString(), rs.getLong(2), rs.getLong(3), kind);
                }).list();
    }
}
