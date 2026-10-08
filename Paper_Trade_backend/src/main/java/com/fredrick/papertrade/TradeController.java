package com.fredrick.papertrade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class TradeController {

    record TradeRequest(@Pattern(regexp = "[A-Z]{1,5}") String symbol,
                        @Pattern(regexp = "BUY|SELL") String type,
                        @Min(1) @Max(10000) int quantity) {}
    record TradeResponse(String symbol, String type, int quantity, double price, double total, double balance) {}
    record HoldingDto(String symbol, int quantity, double avgPrice) {}
    record HoldingsResponse(double balance, List<HoldingDto> holdings) {}

    private final JwtService jwt;
    private final StockService stocks;
    private final UserRepository users;
    private final HoldingRepository holdings;
    private final TradeRepository trades;

    public TradeController(JwtService jwt, StockService stocks, UserRepository users,
                           HoldingRepository holdings, TradeRepository trades) {
        this.jwt = jwt;
        this.stocks = stocks;
        this.users = users;
        this.holdings = holdings;
        this.trades = trades;
    }

    // Buy or sell at the LIVE price. The price always comes from the market-data API on the
    // server, never from the app. @Transactional = balance, holding and trade all save together or not at all.
    @PostMapping("/trades")
    @Transactional
    public TradeResponse trade(@RequestHeader(value = "Authorization", defaultValue = "") String header,
                               @Valid @RequestBody TradeRequest req) {
        Long userId = jwt.userIdFromHeader(header);

        StockService.Stock quote = stocks.quote(req.symbol());
        if (quote == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Live price unavailable for " + req.symbol());

        double price = quote.price();
        double total = Math.round(price * req.quantity() * 100.0) / 100.0;
        User user = users.findByIdForUpdate(userId).orElseThrow();
        Holding holding = holdings.findByUserIdAndSymbol(userId, req.symbol()).orElse(null);

        if (req.type().equals("BUY")) {
            if (user.getBalance() < total) throw rejected("Insufficient balance");
            if (holding == null) holding = new Holding(userId, req.symbol());
            int newQty = holding.getQuantity() + req.quantity();
            // average price = (old cost + new cost) / total shares
            holding.setAvgPrice((holding.getAvgPrice() * holding.getQuantity() + total) / newQty);
            holding.setQuantity(newQty);
            holdings.save(holding);
            user.setBalance(round(user.getBalance() - total));
        } else {
            if (holding == null || holding.getQuantity() < req.quantity()) throw rejected("Not enough shares to sell");
            int newQty = holding.getQuantity() - req.quantity();
            if (newQty == 0) holdings.delete(holding);
            else { holding.setQuantity(newQty); holdings.save(holding); }
            user.setBalance(round(user.getBalance() + total));
        }

        users.save(user);
        trades.save(new Trade(userId, req.symbol(), req.type(), req.quantity(), price, total));
        return new TradeResponse(req.symbol(), req.type(), req.quantity(), price, total, user.getBalance());
    }

    @GetMapping("/holdings")
    public HoldingsResponse holdings(@RequestHeader(value = "Authorization", defaultValue = "") String header) {
        Long userId = jwt.userIdFromHeader(header);
        double balance = users.findById(userId).orElseThrow().getBalance();
        List<HoldingDto> list = holdings.findByUserId(userId).stream()
                .map(h -> new HoldingDto(h.getSymbol(), h.getQuantity(), Math.round(h.getAvgPrice() * 100.0) / 100.0))
                .sorted((a, b) -> a.symbol().compareTo(b.symbol()))
                .toList();
        return new HoldingsResponse(balance, list);
    }

    private static ResponseStatusException rejected(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, message);   // 422: valid request, rule not met
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
