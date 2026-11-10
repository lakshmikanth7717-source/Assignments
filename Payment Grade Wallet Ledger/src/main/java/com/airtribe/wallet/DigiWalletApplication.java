package com.airtribe.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@EnableScheduling
public class DigiWalletApplication {
    public static void main(String[] args) {
        SpringApplication.run(DigiWalletApplication.class, args);
    }
}
