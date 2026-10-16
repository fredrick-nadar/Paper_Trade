package com.fredrick.papertrade;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class Health {

    private static final Logger logger = LoggerFactory.getLogger(Health.class);

    // Simple unauthenticated endpoint used to confirm that the backend process is running.
    @GetMapping("/")
    public String serverStatus() {
        logger.info("📱 BACKEND PINGED FROM ANDROID");
        return "Server is ON";
    }
}