package com.fredrick.papertrade;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Health {

    // Simple unauthenticated endpoint used to confirm that the backend process is running.
    @GetMapping("/")
    public String serverStatus() {
        return "Server is ON";
    }
}