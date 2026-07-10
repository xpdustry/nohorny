// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration;

@SpringBootApplication(exclude = SpringShellAutoConfiguration.class)
public class NoHornyServerApplication {}
