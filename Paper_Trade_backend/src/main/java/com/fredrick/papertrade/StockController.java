package com.fredrick.papertrade;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StockController {

    private final JwtService jwt;
    private final StockService stocks;

    public StockController(JwtService jwt, StockService stocks) {
        this.jwt = jwt;
        this.stocks = stocks;
    }

    // Returns the cached watch list only to authenticated app sessions.
    @GetMapping("/stocks")
    public StockService.StocksResponse stocks(@RequestHeader(value = "Authorization", defaultValue = "") String header) {
        jwt.userIdFromHeader(header);
        return stocks.stocks();
    }

    // Returns chart candles after validating the session, symbol, and requested range.
    @GetMapping("/stocks/{symbol}/history")
    public StockService.History history(@RequestHeader(value = "Authorization", defaultValue = "") String header,
                                        @PathVariable String symbol,
                                        @RequestParam(defaultValue = "1M") String range) {
        jwt.userIdFromHeader(header);
        if (!stocks.isKnown(symbol)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown stock");
        if (!StockService.isValidRange(range)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown range");
        return stocks.history(symbol, range);
    }
}
