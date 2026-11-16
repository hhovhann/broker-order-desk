package dev.brokerdesk.repo;

import dev.brokerdesk.domain.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findTop20BySymbolOrderByIdDesc(String symbol);
    List<Trade> findTop30BySymbolOrderByIdDesc(String symbol);
    List<Trade> findTop50ByBuyAccountIdOrSellAccountIdOrderByIdDesc(long buyAccountId, long sellAccountId);

    interface SymbolStats {
        String getSymbol();
        long getTrades();
        long getShares();
        long getValueMills();
        long getHigh();
        long getLow();
    }

    @Query("select t.symbol as symbol, count(t) as trades, sum(t.quantity) as shares, sum(t.priceMills * t.quantity) as valueMills, "
            + "max(t.priceMills) as high, min(t.priceMills) as low from Trade t group by t.symbol")
    List<SymbolStats> statsBySymbol();
}
