package dev.brokerdesk.web;

import dev.brokerdesk.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/** Market-wide and per-user feature endpoints. Kept in one file because each is a thin adapter over a service. */
@RestController
@RequestMapping("/api/v1")
public class FeatureControllers {
    private final MarketHistoryService history;
    private final MarketSummaryService summary;
    private final AnalyticsService analytics;
    private final NewsService news;
    private final FavoriteService favorites;
    private final AlertService alerts;
    private final WalletService wallet;
    private final LiquidityService liquidity;
    private final ApplicationService applications;
    private final StreamHub hub;
    private final CurrentAccount me;

    public FeatureControllers(MarketHistoryService history, MarketSummaryService summary, AnalyticsService analytics, NewsService news, FavoriteService favorites, AlertService alerts,
                              WalletService wallet, LiquidityService liquidity, ApplicationService applications, StreamHub hub, CurrentAccount me) {
        this.history = history; this.summary = summary; this.analytics = analytics; this.news = news; this.favorites = favorites; this.alerts = alerts;
        this.wallet = wallet; this.liquidity = liquidity; this.applications = applications; this.hub = hub; this.me = me;
    }

    // ---- market (any signed-in user)
    @GetMapping("/market/summary") MarketSummaryService.Summary summary() { return summary.summary(); }
    @GetMapping("/market/{symbol}/candles") List<MarketHistoryService.Candle> candles(@PathVariable String symbol, @RequestParam(defaultValue = "1Y") String range) { return history.candles(symbol, range); }
    @GetMapping("/market/compare") List<MarketHistoryService.Series> compare(@RequestParam List<String> symbols, @RequestParam(defaultValue = "1Y") String range) { return history.compare(symbols, range); }
    @GetMapping("/analytics") List<AnalyticsService.Row> analytics() { return analytics.rows(); }
    @GetMapping("/news") List<NewsService.Item> news(@RequestParam(defaultValue = "NEWS") String kind, @RequestParam(required = false) String symbol) { return news.list(kind, symbol); }
    @GetMapping("/liquidity/members") List<LiquidityService.Member> members() { return liquidity.members(); }
    @GetMapping("/liquidity/{symbol}/activity") List<LiquidityService.Print> activity(@PathVariable String symbol) { return liquidity.activity(symbol); }
    @GetMapping("/wallet/banks") List<WalletService.Bank> banks() { return WalletService.BANKS; }

    /** Live "something changed" stream. The browser refetches what it shows; payloads carry no money values. */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    SseEmitter stream(Authentication auth) { return hub.register(auth.getName()); }

    // ---- favorites
    @GetMapping("/me/favorites") List<String> favorites(Authentication a) { return favorites.list(a.getName()); }
    @PutMapping("/me/favorites/{symbol}") void addFavorite(@PathVariable String symbol, Authentication a) { favorites.add(a.getName(), symbol); }
    @DeleteMapping("/me/favorites/{symbol}") void removeFavorite(@PathVariable String symbol, Authentication a) { favorites.remove(a.getName(), symbol); }

    // ---- alerts
    public record AlertRequest(@NotBlank @Pattern(regexp = "[A-Z0-9]{2,12}") String symbol, @NotBlank String direction, @Positive @Max(1_000_000_000) long priceMills) {}
    @GetMapping("/alerts") List<AlertService.Alert> alerts(Authentication a) { return alerts.list(a.getName()); }
    @PostMapping("/alerts") @ResponseStatus(HttpStatus.CREATED) AlertService.Alert createAlert(@Valid @RequestBody AlertRequest r, Authentication a) { return alerts.create(a.getName(), r.symbol(), r.direction(), r.priceMills()); }
    @DeleteMapping("/alerts/{id}") void deleteAlert(@PathVariable long id, Authentication a) { alerts.delete(a.getName(), id); }

    // ---- wallet (accounts that trade)
    public record TransferRequest(@NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9._-]+") String clientRef, @NotBlank String direction, @Positive long amountMills, @NotBlank String bank) {}
    @GetMapping("/wallet") @PreAuthorize("hasAnyRole('TRADER','LIQUIDITY_PROVIDER')") WalletService.Wallet wallet(Authentication a) { return wallet.wallet(me.idOf(a)); }
    @PostMapping("/wallet/transfers") @PreAuthorize("hasAnyRole('TRADER','LIQUIDITY_PROVIDER')") @ResponseStatus(HttpStatus.CREATED)
    WalletService.Transfer transfer(@Valid @RequestBody TransferRequest r, Authentication a) { return wallet.transfer(me.idOf(a), r.clientRef(), r.direction(), r.amountMills(), r.bank()); }

    // ---- open account (public) and its review (admin)
    public record ApplicationRequest(@NotBlank @Size(max = 80) String fullName, @NotBlank @Email @Size(max = 120) String email, @NotBlank String type, @Size(max = 300) String note) {}
    @PostMapping("/public/applications") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> apply(@Valid @RequestBody ApplicationRequest r, HttpServletRequest req) {
        return Map.of("applicationId", applications.submit(req.getRemoteAddr(), r.fullName(), r.email(), r.type(), r.note()), "status", "PENDING");
    }
    @GetMapping("/admin/applications") @PreAuthorize("hasRole('ADMIN')") List<ApplicationService.Application> applications() { return applications.list(); }
}
