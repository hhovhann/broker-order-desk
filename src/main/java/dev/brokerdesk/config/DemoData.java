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
    private final TradeRepository trades;
    private final HistorySeeder history;

    public DemoData(InstrumentRepository instruments, AccountRepository accounts, HoldingRepository holdings, TradeRepository trades, HistorySeeder history) {
        this.history = history;
        this.instruments = instruments; this.accounts = accounts; this.holdings = holdings; this.trades = trades;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (accounts.findByOwner("trader1").isPresent()) return;
        instruments.save(new Instrument("SBNK", "Sample Bank (synthetic)", 12_500));
        instruments.save(new Instrument("STEL", "Sample Telecom (synthetic)", 9_800));
        seedOpeningSession();
        Account t1 = accounts.save(new Account("trader1", 100_000_000, 400_000_000));      // 100,000 QAR cash, 400,000 QAR margin line
        Account t2 = accounts.save(new Account("trader2", 50_000_000, 0));
        Account lp = accounts.save(new Account("lp1", 500_000_000, 0).asLiquidityProvider());
        Account lp2 = accounts.save(new Account("lp2", 300_000_000, 0).asLiquidityProvider());
        holdings.save(new Holding(lp2.getId(), "STEL", 40_000));
        holdings.save(new Holding(lp.getId(), "SBNK", 50_000));
        holdings.save(new Holding(t2.getId(), "SBNK", 100_000));
        history.seed(instruments.findAll());
    }

    /** Ten invented listings with a random-walk of opening-session prints, so the price board has statistics and trends. SBNK and STEL stay untouched for the demo story. */
    private void seedOpeningSession() {
        String[][] listed = {{"FNBK", "First National Bank (synthetic)", "18300"}, {"GTEL", "Gulf Telecom (synthetic)", "12900"}, {"INDX", "Industrial Holdings (synthetic)", "10420"},
                {"PETR", "Petrochem Group (synthetic)", "3130"}, {"REAL", "Real Estate Dev (synthetic)", "1322"}, {"INSU", "Insurance Group (synthetic)", "2160"},
                {"HLTH", "Healthcare Co (synthetic)", "1307"}, {"FOOD", "Foods Company (synthetic)", "4880"}, {"LOGI", "Logistics Co (synthetic)", "4250"}, {"ENRG", "Energy Utilities (synthetic)", "14040"}};
        java.util.Random rnd = new java.util.Random(42);
        for (String[] l : listed) {
            long ref = Long.parseLong(l[2]);
            Instrument i = instruments.save(new Instrument(l[0], l[1], ref));
            long price = ref;
            int prints = 18 + rnd.nextInt(14);
            for (int n = 0; n < prints; n++) {
                price = Math.max(ref * 92 / 100, Math.min(ref * 108 / 100, price + Math.round(price * (rnd.nextGaussian() * 0.006 + 0.0004))));
                trades.save(new Trade(l[0], price, 100 + rnd.nextInt(4900), 0, 0, 0, 0));   // account 0: opening-session print, not tied to any demo account
            }
            i.traded(price);
        }
    }
}
