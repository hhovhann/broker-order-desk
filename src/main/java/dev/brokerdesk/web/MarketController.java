package dev.brokerdesk.web;

import dev.brokerdesk.engine.MatchingEngine;
import dev.brokerdesk.engine.OrderBook;
import dev.brokerdesk.repo.*;
import dev.brokerdesk.service.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/market")
public class MarketController {
    public record TradePrint(long priceMills, long quantity, String executedAt) {}
    public record InstrumentView(String symbol, String name, long referencePriceMills, long lastPriceMills) {}
    /** One row of the price board: best offer and bid from the live book, session statistics from the trades. */
    public record BoardRow(String symbol, String name, long offerShares, long offerPriceMills, long lastPriceMills, long bidPriceMills, long bidShares,
                           long trades, long averageMills, long shares, long valueMills, long prevCloseMills, long highMills, long lowMills,
                           long changeMills, long changeBps, List<Long> spark) {}

    public record Rules(int bandPercent, int maintenanceMarginPercent, long maxOrderValueMills) {}

    private final MatchingEngine engine;
    private final dev.brokerdesk.config.BrokerProperties props;
    private final TradeRepository trades;
    private final InstrumentRepository instruments;

    public MarketController(MatchingEngine engine, TradeRepository trades, InstrumentRepository instruments, dev.brokerdesk.config.BrokerProperties props) {
        this.props = props;
        this.engine = engine; this.trades = trades; this.instruments = instruments;
    }

    @GetMapping("/instruments")
    List<InstrumentView> instruments() {
        return instruments.findAll().stream().map(i -> new InstrumentView(i.getSymbol(), i.getName(), i.getReferencePriceMills(), i.getLastPriceMills())).toList();
    }

    /** The business limits the pre-trade checks use, so the UI can show them next to the order ticket. The server stays the only enforcer. */
    @GetMapping("/rules")
    Rules rules() { return new Rules(props.bandPercent(), props.maintenanceMarginPercent(), props.maxOrderValueMills()); }

    @GetMapping("/board")
    List<BoardRow> board() {
        var all = instruments.findAll();
        Map<String, TradeRepository.SymbolStats> stats = trades.statsBySymbol().stream().collect(Collectors.toMap(TradeRepository.SymbolStats::getSymbol, s -> s));
        Map<String, CompletableFuture<OrderBook.Depth>> depth = new HashMap<>();
        all.forEach(i -> depth.put(i.getSymbol(), engine.depth(i.getSymbol(), 1)));
        return all.stream().map(i -> {
            OrderBook.Depth d = depth.get(i.getSymbol()).join();
            var st = stats.get(i.getSymbol());
            var offer = d.asks().isEmpty() ? null : d.asks().get(0);
            var bid = d.bids().isEmpty() ? null : d.bids().get(0);
            List<Long> spark = new ArrayList<>(trades.findTop30BySymbolOrderByIdDesc(i.getSymbol()).stream().map(t -> t.getPriceMills()).toList());
            Collections.reverse(spark);
            long change = i.getLastPriceMills() - i.getReferencePriceMills();
            return new BoardRow(i.getSymbol(), i.getName(), offer == null ? 0 : offer.quantity(), offer == null ? 0 : offer.priceMills(), i.getLastPriceMills(),
                    bid == null ? 0 : bid.priceMills(), bid == null ? 0 : bid.quantity(),
                    st == null ? 0 : st.getTrades(), st == null || st.getShares() == 0 ? 0 : st.getValueMills() / st.getShares(), st == null ? 0 : st.getShares(),
                    st == null ? 0 : st.getValueMills(), i.getReferencePriceMills(), st == null ? 0 : st.getHigh(), st == null ? 0 : st.getLow(),
                    change, change * 10_000 / i.getReferencePriceMills(), spark);
        }).toList();
    }

    @GetMapping("/{symbol}/book")
    CompletableFuture<OrderBook.Depth> book(@PathVariable String symbol) {
        if (!instruments.existsById(symbol)) throw new ApiException(HttpStatus.NOT_FOUND, "unknown symbol");
        return engine.depth(symbol, 5);
    }

    @GetMapping("/{symbol}/trades")
    List<TradePrint> tape(@PathVariable String symbol) {
        return trades.findTop20BySymbolOrderByIdDesc(symbol).stream().map(t -> new TradePrint(t.getPriceMills(), t.getQuantity(), t.getExecutedAt().toString())).toList();
    }
}
