package com.nordia.rooms;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class NordiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(NordiaApplication.class, args);
    }

    /** The clock is a bean so tests can replace it (needed for the "cancel before start" rule). */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
