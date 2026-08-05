// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.persistence.PersistenceConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration;

@SpringBootApplication(exclude = SpringShellAutoConfiguration.class)
@Import(PersistenceConfiguration.class)
public class NoHornyServerApplication {}
