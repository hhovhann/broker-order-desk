package dev.brokerdesk.config;

import dev.brokerdesk.domain.Instrument;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/** Synthetic history, financials and news for the demo profile. Deterministic per symbol so screenshots and tests are stable. Invented content only. */
@Component
class HistorySeeder {
    private final JdbcClient jdbc;
    HistorySeeder(JdbcClient jdbc) { this.jdbc = jdbc; }

    void seed(List<Instrument> instruments) {
        LocalDate end = LocalDate.now().minusDays(1);
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = end.minusDays(1100); !d.isAfter(end); d = d.plusDays(1))
            if (d.getDayOfWeek() != DayOfWeek.FRIDAY && d.getDayOfWeek() != DayOfWeek.SATURDAY) days.add(d);
        for (Instrument inst : instruments) { history(inst, days); financials(inst); }
        news(instruments);
    }

    private void history(Instrument inst, List<LocalDate> days) {
        Random rnd = new Random(inst.getSymbol().hashCode());
        double[] close = new double[days.size()];
        double p = 100, drift = rnd.nextGaussian() * 0.0002;
        for (int i = 0; i < close.length; i++) { p *= 1 + drift + rnd.nextGaussian() * 0.011; p = Math.max(p, 20); close[i] = p; }
        double scale = inst.getReferencePriceMills() / close[close.length - 1];   // the last candle closes at the previous close
        for (int i = 0; i < close.length; i++) {
            double c = close[i] * scale, o = i == 0 ? c : close[i - 1] * scale;
            double hi = Math.max(o, c) * (1 + rnd.nextDouble() * 0.008), lo = Math.min(o, c) * (1 - rnd.nextDouble() * 0.008);
            jdbc.sql("insert into price_history (symbol, trade_date, open_mills, high_mills, low_mills, close_mills, volume) values (:s, :d, :o, :h, :l, :c, :v)")
                    .param("s", inst.getSymbol()).param("d", java.sql.Date.valueOf(days.get(i))).param("o", Math.round(o)).param("h", Math.round(hi)).param("l", Math.round(lo))
                    .param("c", Math.max(1, Math.round(c))).param("v", 50_000 + rnd.nextInt(5_000_000)).update();
        }
    }

    private void financials(Instrument inst) {
        Random rnd = new Random(inst.getSymbol().hashCode() * 31L);
        long shares = 200_000_000L + (long) (rnd.nextDouble() * 9_000_000_000L);
        double pb = 0.6 + rnd.nextDouble() * 2.6, pe = rnd.nextInt(12) == 0 ? -(20 + rnd.nextDouble() * 40) : 7 + rnd.nextDouble() * 14, yield = rnd.nextInt(5) == 0 ? 0 : rnd.nextDouble() * 0.06;
        long price = inst.getLastPriceMills();
        long equity = Math.round(price * (double) shares / pb), profit = Math.round(price * (double) shares / pe);
        long assets = Math.round(equity * (1.1 + rnd.nextDouble() * 6));
        jdbc.sql("insert into financials (symbol, period, shares_outstanding, assets_mills, liabilities_mills, profit_mills, dividend_per_share_mills, up_days_year) values (:s, 'Q3-2026', :sh, :a, :l, :p, :d, :u)")
                .param("s", inst.getSymbol()).param("sh", shares).param("a", assets).param("l", assets - equity).param("p", profit).param("d", Math.round(price * yield)).param("u", 85 + rnd.nextInt(45)).update();
    }

    private void news(List<Instrument> instruments) {
        LocalDate today = LocalDate.now();
        String[][] items = {
                {"NEWS", null, "Synthetic index constituents to be reviewed in the semi-annual review", "The market operator announced that constituent changes will take effect next month. This is invented sample text."},
                {"NEWS", null, "Trading volumes rise as financial results season begins", "Average daily value traded rose against the previous month in this invented sample item."},
                {"NEWS", null, "Regulator publishes consultation on margin financing limits", "A synthetic consultation paper invites comments from licensed brokers. No real regulator is referred to."},
                {"NEWS", null, "New listing opens opportunities in the consumer goods sector", "A fictional company completes its listing. Sample text for the demo."},
                {"NEWS", "SBNK", "Sample Bank announces a change in its executive committee", "An invented announcement used to show company-level news."},
                {"NEWS", "STEL", "Sample Telecom extends its network coverage", "An invented announcement used to show company-level news."},
                {"DISCLOSURE", "SBNK", "Disclosure of financial statements for the third quarter", "Net profit per share was QR 0.253 against QR 0.241 for the same period last year (synthetic)."},
                {"DISCLOSURE", "STEL", "Conference call date", "To discuss the financial results for Q3 2026 (synthetic)."},
                {"DISCLOSURE", "FNBK", "Board meeting date", "To discuss the annual results and the proposed distribution (synthetic)."},
                {"DISCLOSURE", "GTEL", "Disclosure of financial statements for the fourth quarter", "Revenue grew on higher subscriber numbers (synthetic)."},
                {"DISCLOSURE", "PETR", "Notice of general assembly", "The ordinary general assembly will meet to approve the accounts (synthetic)."},
                {"DISCLOSURE", "REAL", "Loss per share for the third quarter", "The loss per share amounted to QR 0.012 versus earnings of QR 0.043 for the same period in 2025 (synthetic)."}};
        Set<String> known = new HashSet<>();
        instruments.forEach(i -> known.add(i.getSymbol()));
        int n = 0;
        for (String[] it : items) {
            if (it[1] != null && !known.contains(it[1])) continue;
            jdbc.sql("insert into news_item (kind, symbol, published_on, title, body) values (:k, :s, :d, :t, :b)").param("k", it[0]).param("s", it[1])
                    .param("d", java.sql.Date.valueOf(today.minusDays(n++ / 2))).param("t", it[2]).param("b", it[3]).update();
        }
    }
}
