// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.cli;

import com.xpdustry.nohorny.persistence.PersistenceConfiguration;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(PersistenceConfiguration.class)
public class NoHornyCliApplication {

    static void main(final String[] args) {
        new SpringApplicationBuilder(NoHornyCliApplication.class)
                .web(WebApplicationType.NONE)
                .lazyInitialization(true)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .properties("spring.shell.interactive.enabled=false", "spring.shell.context.close=true")
                .run(args.length == 0 ? new String[] {"help"} : args);
    }
}
