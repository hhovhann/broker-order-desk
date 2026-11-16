package dev.brokerdesk.config;

import dev.brokerdesk.domain.*;
import dev.brokerdesk.repo.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Synthetic demo data (profile "demo"). Invented names, prices and balances; not market data. */
@Component
@Profile("demo")
public class DemoData implements ApplicationRunner {
    private final InstrumentRepository instruments;
    private final AccountRepository accounts;
    private final HoldingRepository holdings;

    public DemoData(InstrumentRepository instruments, AccountRepository accounts, HoldingRepository holdings) {
        this.instruments = instruments; this.accounts = accounts; this.holdings = holdings;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (accounts.findByOwner("trader1").isPresent()) return;
        instruments.save(new Instrument("SBNK", "Sample Bank (synthetic)", 12_500));
        instruments.save(new Instrument("STEL", "Sample Telecom (synthetic)", 9_800));
        Account t1 = accounts.save(new Account("trader1", 100_000_000, 400_000_000));      // 100,000 QAR cash, 400,000 QAR margin line
        Account t2 = accounts.save(new Account("trader2", 50_000_000, 0));
        Account lp = accounts.save(new Account("lp1", 500_000_000, 0));
        holdings.save(new Holding(lp.getId(), "SBNK", 50_000));
        holdings.save(new Holding(t2.getId(), "SBNK", 100_000));
    }
}
