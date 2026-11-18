// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.database;

import java.sql.SQLException;

@FunctionalInterface
public interface SQLBiConsumer<V1, V2> {
    void accept(V1 value1, V2 value2) throws SQLException;
}
