package dev.brokerdesk.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;

/** Financial statements and ratios per company. Figures are synthetic; ratios are computed live from the last price. */
@Service
public class AnalyticsService {
    public record Row(String symbol, String name, String period, long lastPriceMills, long assetsMills, long liabilitiesMills, long equityMills, long profitMills,
                      Long profitOverEquityBps, Long epsMills, Long dividendYieldBps, Long bookValueMills, Long peX1000, Long pbX1000, int upDaysYear) {}

    private final JdbcClient jdbc;
    public AnalyticsService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public List<Row> rows() {
        return jdbc.sql("select i.symbol, i.name, i.last_price_mills, f.period, f.shares_outstanding, f.assets_mills, f.liabilities_mills, f.profit_mills, f.dividend_per_share_mills, f.up_days_year "
                + "from financials f join instrument i on i.symbol = f.symbol order by i.symbol").query((rs, n) -> {
            var r = Ratios.compute(new Ratios.Input(rs.getLong("shares_outstanding"), rs.getLong("assets_mills"), rs.getLong("liabilities_mills"), rs.getLong("profit_mills"),
                    rs.getLong("dividend_per_share_mills"), rs.getLong("last_price_mills")));
            return new Row(rs.getString("symbol"), rs.getString("name"), rs.getString("period"), rs.getLong("last_price_mills"), rs.getLong("assets_mills"), rs.getLong("liabilities_mills"),
                    r.equityMills(), rs.getLong("profit_mills"), r.profitOverEquityBps(), r.epsMills(), r.dividendYieldBps(), r.bookValueMills(), r.peX1000(), r.pbX1000(), rs.getInt("up_days_year"));
        }).list();
    }
}
