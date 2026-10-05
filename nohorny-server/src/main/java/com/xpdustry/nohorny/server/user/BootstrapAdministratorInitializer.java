// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.user;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/// Applies the bootstrap administrator configuration on startup, before the server accepts requests.
@Component
public final class BootstrapAdministratorInitializer implements SmartInitializingSingleton {

    private final UserService users;

    public BootstrapAdministratorInitializer(final UserService users) {
        this.users = users;
    }

    @Override
    public void afterSingletonsInstantiated() {
        this.users.ensureBootstrapAdministrator();
    }
}
