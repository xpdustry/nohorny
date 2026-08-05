// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.cli;

import com.xpdustry.nohorny.server.NoHornyServerApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.shell.core.command.annotation.Arguments;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

@Component
public final class StartCommand {

    @Command(name = "start", description = "Start the NoHorny classification server.")
    public void onStart(final @Arguments String[] arguments) {
        final var application = new SpringApplication(NoHornyServerApplication.class);
        application.setMainApplicationClass(NoHornyServerApplication.class);
        application.run(arguments);
    }
}
