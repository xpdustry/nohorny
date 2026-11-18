// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;

/// Runs raw SQL in transactions. SQLite has a single writer, so the write transactions take its lock as they begin
/// and wait for each other, while the read transactions run alongside them on a snapshot of the committed data.
///
/// A call made while the thread, or one of its scoped children, runs a transaction of this database joins it rather
/// than opening its own: it sees the uncommitted changes and shares the commit or the rollback. A joined call that
/// throws rolls the whole transaction back, even if its caller catches the exception.
public interface SQLiteDatabase {

    /// Runs the function in a transaction holding the write lock, committed once it returns and rolled back if it
    /// throws. The next write transaction only begins once the [Transaction#afterCommit] callbacks of this one ran.
    ///
    /// @throws IllegalStateException if called from a read transaction, which cannot take the write lock safely, or
    ///     from a callback, whose write would commit before the remaining callbacks of its transaction
    <R> R withTransaction(SQLFunction<Transaction, R> function);

    /// Runs the function in a transaction that only reads, alongside the writer. It joins a write transaction too.
    <R> R withReadTransaction(SQLFunction<Transaction, R> function);

    interface Transaction {

        StatementBuilder prepareStatement(@Language("SQL") String statement) throws SQLException;

        /// Runs the action once the outermost transaction committed. A failing action is logged, the others still run.
        /// The action can read, but not write.
        void afterCommit(Runnable action);

        /// Runs the action once the outermost transaction rolled back. A failing action is logged, the others still
        /// run. The action can read, but not write.
        void afterRollback(Runnable action);
    }

    /// Binds the parameters in order. The instants are stored as epoch milliseconds and the enums by name, like the
    /// whole schema.
    interface StatementValueBinder<B extends StatementValueBinder<B>> {

        B bind(@Nullable String value) throws SQLException;

        B bind(int value) throws SQLException;

        B bind(long value) throws SQLException;

        B bind(boolean value) throws SQLException;

        B bind(double value) throws SQLException;

        B bind(@Nullable Double value) throws SQLException;

        B bind(Instant value) throws SQLException;

        B bind(@Nullable Enum<?> value) throws SQLException;
    }

    interface StatementBuilder extends StatementValueBinder<StatementBuilderExecute>, StatementBuilderExecute {

        /// Runs the statement once per value, [StatementBuilderExecute#executeUpdate] returns the sum of the updates.
        <T> StatementBuilderExecute batch(Iterable<T> values, SQLBiConsumer<StatementValueBinder<?>, T> consumer)
                throws SQLException;
    }

    interface StatementBuilderExecute extends StatementValueBinder<StatementBuilderExecute> {

        <T> List<T> executeSelect(SQLFunction<ResultSet, T> mapper) throws SQLException;

        /// @throws IllegalStateException if more than one row is selected
        <T> Optional<T> executeSingleSelect(SQLFunction<ResultSet, T> mapper) throws SQLException;

        int executeUpdate() throws SQLException;

        /// @throws IllegalStateException if more than one row is updated
        boolean executeSingleUpdate() throws SQLException;
    }
}
