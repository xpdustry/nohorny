// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NoHornyServerApp {
    static void main(final String[] args) {
        SpringApplication.run(NoHornyServerApp.class, args);
    }
}
