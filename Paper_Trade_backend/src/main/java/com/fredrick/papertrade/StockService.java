package com.fredrick.papertrade;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StockService {

    public record Stock(String symbol, String name, String logo, double price, double change, double changePercent) {}
    public record Profile(String name, String logo) {}
    public record StocksResponse(List<Stock> stocks, long updatedAt) {}

    // Fixed watch list for the demo
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

    /** All watch-list stocks, cached for 30 seconds. */
    public StocksResponse stocks() {
        return load();
    }

    /** One fresh (uncached) quote, used to price a trade. Null if unavailable. */
    public Stock quote(String symbol) {
        return SYMBOLS.contains(symbol) ? fetch(symbol) : null;
    }

    // Reuse the last result for 30 seconds so we don't hammer the market-data API
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

    // Finnhub /quote: c = current price, d = change, dp = change percent
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

    // Finnhub /stock/profile2: company name and logo URL
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

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
