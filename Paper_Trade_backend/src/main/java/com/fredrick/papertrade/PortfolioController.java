package com.fredrick.papertrade;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
public class PortfolioController {

    record Position(String symbol, String name, String logo, int quantity, double avgPrice,
                    double price, double value, double pnl, double pnlPercent) {}
    record Portfolio(double cash, double invested, double value, double pnl, double pnlPercent, List<Position> holdings) {}

    private final JwtService jwt;
    private final StockService stocks;
    private final UserRepository users;
    private final HoldingRepository holdings;

    public PortfolioController(JwtService jwt, StockService stocks, UserRepository users, HoldingRepository holdings) {
        this.jwt = jwt;
        this.stocks = stocks;
        this.users = users;
        this.holdings = holdings;
    }

    // Dashboard numbers: cash, invested cost, live value, profit/loss, and position details.
    @GetMapping("/portfolio")
    public Portfolio portfolio(@RequestHeader(value = "Authorization", defaultValue = "") String header) {
        Long userId = jwt.userIdFromHeader(header);
        double cash = users.findById(userId).orElseThrow().getBalance();

        List<Holding> owned = holdings.findByUserId(userId);
        Map<String, StockService.Stock> live = owned.isEmpty() ? Map.of()
                : stocks.stocks().stocks().stream().collect(Collectors.toMap(StockService.Stock::symbol, Function.identity()));

        double invested = 0, value = 0;
        List<Position> positions = new java.util.ArrayList<>();
        for (Holding h : owned) {
            StockService.Stock s = live.get(h.getSymbol());
            double price = s != null ? s.price() : h.getAvgPrice();   // fall back to cost if no live price
            double cost = h.getAvgPrice() * h.getQuantity();
            double worth = price * h.getQuantity();
            invested += cost;
            value += worth;
            positions.add(new Position(h.getSymbol(), s != null ? s.name() : h.getSymbol(), s != null ? s.logo() : "",
                    h.getQuantity(), r(h.getAvgPrice()), r(price), r(worth), r(worth - cost), pct(worth - cost, cost)));
        }
        positions.sort((a, b) -> a.symbol().compareTo(b.symbol()));
        return new Portfolio(r(cash), r(invested), r(value), r(value - invested), pct(value - invested, invested), positions);
    }

    // Rounds money values at the API boundary so the UI receives stable currency values.
    private static double r(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // Calculates a percentage without dividing by zero for an empty portfolio.
    private static double pct(double part, double whole) {
        return whole == 0 ? 0 : r(part / whole * 100);
    }
}
