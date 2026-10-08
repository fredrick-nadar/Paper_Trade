package com.fredrick.papertrade;

import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/stocks")
    public StockService.StocksResponse stocks(@RequestHeader(value = "Authorization", defaultValue = "") String header) {
        jwt.userIdFromHeader(header);
        return stocks.stocks();
    }
}
