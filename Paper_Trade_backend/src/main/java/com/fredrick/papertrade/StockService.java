package com.fredrick.papertrade;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StockService {

    // These records are the market-data contracts consumed by the Android screens.
    public record Stock(String symbol, String name, String logo, double price, double change, double changePercent) {}
    public record Profile(String name, String logo) {}
    public record StocksResponse(List<Stock> stocks, long updatedAt) {}
    public record Candle(long t, double o, double h, double l, double c) {}
    public record History(String symbol, String range, long firstTradeDate, List<String> available, List<Candle> candles) {}

    // Chart ranges: lookback duration and candle interval used for each range.
    private record Range(int days, String interval) {}
    private static final Map<String, Range> RANGES = new LinkedHashMap<>();
    static {
        RANGES.put("1M", new Range(30, "1d"));
        RANGES.put("3M", new Range(91, "1d"));
        RANGES.put("6M", new Range(182, "1d"));
        RANGES.put("1Y", new Range(365, "1wk"));
        RANGES.put("3Y", new Range(1095, "1wk"));
        RANGES.put("5Y", new Range(1825, "1wk"));
        RANGES.put("10Y", new Range(3650, "1mo"));
        RANGES.put("ALL", new Range(0, "1mo"));
    }
    private static final long HISTORY_CACHE_MS = 5 * 60_000;
    private record CachedHistory(History history, long at) {}
    private final Map<String, CachedHistory> historyCache = new ConcurrentHashMap<>();

    // Fixed watch list for this demo; symbols are the only tradable universe.
    private static final List<String> SYMBOLS =
            List.of("AAPL", "MSFT", "NVDA", "TSLA", "AMZN", "GOOGL", "META", "NFLX", "AMD", "DIS");

    private static final long CACHE_MS = 30_000;

    private final String apiKey;
    private final RestClient http = RestClient.create();
    private StocksResponse cache;
    // Company name + logo never change, so each is fetched only once
    private final Map<String, Profile> profiles = new ConcurrentHashMap<>();

    public StockService(@Value("${finnhub.key}") String apiKey) {
        this.apiKey = apiKey;
    }

    /** Returns watch-list quotes; the result is cached for 30 seconds. */
    public StocksResponse stocks() {
        return load();
    }

    /** Fetches one uncached quote so the server, not the app, determines trade pricing. */
    public Stock quote(String symbol) {
        return SYMBOLS.contains(symbol) ? fetch(symbol) : null;
    }

    public boolean isKnown(String symbol) {
        return SYMBOLS.contains(symbol);
    }

    public static boolean isValidRange(String range) {
        return RANGES.containsKey(range);
    }

    /**
     * OHLC candles for one symbol from Yahoo Finance (Finnhub's candle API is paid-only).
     * "available" lists only the ranges the stock has enough history for, so a recent IPO has no 10Y/5Y.
     */
    public History history(String symbol, String range) {
        String key = symbol + ":" + range;
        CachedHistory hit = historyCache.get(key);
        if (hit != null && System.currentTimeMillis() - hit.at() < HISTORY_CACHE_MS) return hit.history();
        try {
            Range r = RANGES.get(range);
            long now = System.currentTimeMillis() / 1000;
            String window = r.days() == 0 ? "range=max" : "period1=" + (now - r.days() * 86400L) + "&period2=" + now;
            Map<String, Object> body = http.get()
                    .uri("https://query1.finance.yahoo.com/v8/finance/chart/" + symbol + "?" + window + "&interval=" + r.interval())
                    .header("User-Agent", "Mozilla/5.0")
                    .retrieve().body(Map.class);
            History h = parseHistory(symbol, range, body, now);
            historyCache.put(key, new CachedHistory(h, System.currentTimeMillis()));
            return h;
        } catch (Exception e) {
            if (hit != null) return hit.history();   // Yahoo down: serve the stale chart
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Chart data unavailable");
        }
    }

    @SuppressWarnings("unchecked")
    private History parseHistory(String symbol, String range, Map<String, Object> body, long now) {
        Map<String, Object> result = ((List<Map<String, Object>>) ((Map<String, Object>) body.get("chart")).get("result")).get(0);
        long first = ((Number) ((Map<String, Object>) result.get("meta")).get("firstTradeDate")).longValue();
        List<Number> ts = (List<Number>) result.get("timestamp");
        Map<String, List<Number>> q = ((List<Map<String, List<Number>>>) ((Map<String, Object>) result.get("indicators")).get("quote")).get(0);

        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < ts.size(); i++) {
            Number o = q.get("open").get(i), h = q.get("high").get(i), l = q.get("low").get(i), c = q.get("close").get(i);
            if (o == null || h == null || l == null || c == null) continue;   // Yahoo leaves gaps as nulls
            candles.add(new Candle(ts.get(i).longValue() * 1000, round(o.doubleValue()), round(h.doubleValue()),
                    round(l.doubleValue()), round(c.doubleValue())));
        }

        // A range is offered only if the stock already traded at its start (a week of slack for weekends/holidays)
        List<String> available = new ArrayList<>();
        RANGES.forEach((name, r) -> {
            if (r.days() == 0 || first <= now - r.days() * 86400L + 7 * 86400L) available.add(name);
        });
        return new History(symbol, range, first * 1000, available, candles);
    }

    // Reuse the last result for 30 seconds so normal screen refreshes do not hammer Finnhub.
    private synchronized StocksResponse load() {
        if (cache == null || System.currentTimeMillis() - cache.updatedAt() > CACHE_MS) {
            List<Stock> list = SYMBOLS.parallelStream()
                    .map(this::fetch)
                    .filter(s -> s != null)
                    .sorted((a, b) -> a.symbol().compareTo(b.symbol()))
                    .toList();
            if (list.isEmpty() && cache != null) return cache;   // API down: serve the old data
            if (list.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Market data unavailable");
            cache = new StocksResponse(list, System.currentTimeMillis());
        }
        return cache;
    }

    // Maps Finnhub's quote fields: c=current, d=change, dp=change percent.
    private Stock fetch(String symbol) {
        try {
            Map<String, Object> q = get("/quote?symbol=" + symbol);
            double price = ((Number) q.get("c")).doubleValue();
            if (price == 0) return null;   // unknown symbol
            Profile p = profiles.computeIfAbsent(symbol, this::fetchProfile);
            return new Stock(symbol, p.name(), p.logo(), round(price),
                    round(((Number) q.get("d")).doubleValue()), round(((Number) q.get("dp")).doubleValue()));
        } catch (Exception e) {
            return null;
        }
    }

    // Fetches display metadata separately because it changes less often than prices.
    private Profile fetchProfile(String symbol) {
        Map<String, Object> p = get("/stock/profile2?symbol=" + symbol);
        return new Profile(String.valueOf(p.getOrDefault("name", symbol)), String.valueOf(p.getOrDefault("logo", "")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path) {
        String sep = path.contains("?") ? "&" : "?";
        return http.get().uri("https://finnhub.io/api/v1" + path + sep + "token=" + apiKey)
                .retrieve().body(Map.class);
    }

    // Keeps prices consistent between the backend response and the Compose UI.
    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
