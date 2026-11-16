package dev.brokerdesk.service;

import dev.brokerdesk.domain.Account;
import dev.brokerdesk.repo.AccountRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cash in and out through simulated bank rails. Withdrawals can never reach into the margin loan or into cash reserved by open buy orders.
 * Idempotent by (account, clientRef).
 */
@Service
public class WalletService {
    public record Bank(String name, String rail) {}
    public record Transfer(long id, String clientRef, String direction, long amountMills, String bank, String rail, String status, String createdAt) {}
    public record Wallet(long cashMills, long reservedMills, long withdrawableMills, List<Transfer> transfers) {}

    /** Synthetic bank names. Rails mirror the shape of a national scheme: instant (SAHL+), standard (SAHL) and transfer (FAST). */
    public static final List<Bank> BANKS = List.of(new Bank("Demo Bank A", "SAHL_PLUS"), new Bank("Demo Bank B", "SAHL_PLUS"), new Bank("Demo Bank C", "SAHL_PLUS"),
            new Bank("Demo Bank D", "SAHL"), new Bank("Demo Bank E", "SAHL"), new Bank("Demo Bank F", "FAST"), new Bank("Demo Bank G", "FAST"));
    private static final long MAX = 1_000_000_000_000L;

    private final AccountRepository accounts;
    private final JdbcClient jdbc;
    private final AuditService audit;

    public WalletService(AccountRepository accounts, JdbcClient jdbc, AuditService audit) { this.accounts = accounts; this.jdbc = jdbc; this.audit = audit; }

    @Transactional(readOnly = true)
    public Wallet wallet(long accountId) {
        Account a = accounts.findById(accountId).orElseThrow();
        return new Wallet(a.getCashMills(), a.getReservedMills(), Math.max(0, a.getCashMills() - a.getReservedMills()), transfers(accountId));
    }

    List<Transfer> transfers(long accountId) {
        return jdbc.sql("select id, client_ref, direction, amount_mills, bank, rail, status, created_at from cash_transfer where account_id = :a order by id desc limit 30").param("a", accountId)
                .query((rs, i) -> new Transfer(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getTimestamp(8).toString())).list();
    }

    @Transactional
    public Transfer transfer(long accountId, String clientRef, String direction, long amountMills, String bank) {
        if (!direction.equals("DEPOSIT") && !direction.equals("WITHDRAW")) throw new ApiException(HttpStatus.BAD_REQUEST, "direction must be DEPOSIT or WITHDRAW");
        if (amountMills <= 0 || amountMills > MAX) throw new ApiException(HttpStatus.BAD_REQUEST, "amount out of range");
        Bank b = BANKS.stream().filter(x -> x.name().equals(bank)).findFirst().orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "unknown bank"));
        Account a = accounts.lockById(accountId).orElseThrow();
        var existing = jdbc.sql("select id from cash_transfer where account_id = :a and client_ref = :r").param("a", accountId).param("r", clientRef).query(Long.class).optional();
        if (existing.isPresent()) {
            Transfer t = transfers(accountId).stream().filter(x -> x.id() == existing.get()).findFirst().orElseThrow();
            if (!t.direction().equals(direction) || t.amountMills() != amountMills || !t.bank().equals(bank)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "clientRef already used with different details");
            return t;
        }
        if (direction.equals("WITHDRAW")) {
            if (a.loanMills() > 0) throw new ApiException(HttpStatus.CONFLICT, "repay the margin loan before withdrawing");
            if (amountMills > a.getCashMills() - a.getReservedMills()) throw new ApiException(HttpStatus.CONFLICT, "amount exceeds withdrawable cash");
            a.debit(amountMills);
        } else a.credit(amountMills);
        try {
            jdbc.sql("insert into cash_transfer (account_id, client_ref, direction, amount_mills, bank, rail, status, created_at) values (:a, :r, :d, :m, :b, :rail, 'COMPLETED', current_timestamp)")
                    .param("a", accountId).param("r", clientRef).param("d", direction).param("m", amountMills).param("b", b.name()).param("rail", b.rail()).update();
        } catch (DuplicateKeyException race) { throw new ApiException(HttpStatus.CONFLICT, "duplicate transfer"); }
        audit.record("TRANSFER_" + direction, amountMills + " mills via " + b.rail() + " " + b.name() + " ref=" + clientRef);
        return transfers(accountId).get(0);
    }
}
