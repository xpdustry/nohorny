// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.database;

import java.sql.SQLException;

@FunctionalInterface
public interface SQLFunction<I, O> {
    O apply(I input) throws SQLException;
}
