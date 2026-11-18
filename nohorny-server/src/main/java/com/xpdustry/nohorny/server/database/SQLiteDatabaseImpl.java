// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

/// The transactions are opened with SQL rather than by turning off the auto-commit of the connections: the SQLite
/// driver would begin the next transaction as soon as one ends, so every idle connection of the pool would hold the
/// write lock.
///
/// Created once Flyway migrated the schema, the transactions of its users can run in their constructors.
@Component
@DependsOnDatabaseInitialization
public final class SQLiteDatabaseImpl implements SQLiteDatabase {

    private static final Logger log = LoggerFactory.getLogger(SQLiteDatabaseImpl.class);
    private static final ScopedValue<TransactionImpl> HANDLE = ScopedValue.newInstance();

    private final DataSource source;

    // Taken before the connection, so the waiting writers leave the pool to the readers rather than waiting on the
    // busy timeout of SQLite. Released after the commit callbacks, see withTransaction.
    private final ReentrantLock writer = new ReentrantLock();

    public SQLiteDatabaseImpl(final DataSource source) {
        this.source = source;
    }

    @Override
    public <R> R withTransaction(final SQLFunction<Transaction, R> function) {
        final var current = this.current();
        if (current != null) {
            if (current.readOnly) {
                throw new IllegalStateException("A write transaction cannot join a read transaction");
            }
            return join(current, function);
        }
        this.writer.lock();
        try {
            return this.run("BEGIN IMMEDIATE", false, function);
        } finally {
            this.writer.unlock();
        }
    }

    @Override
    public <R> R withReadTransaction(final SQLFunction<Transaction, R> function) {
        final var current = this.current();
        return current != null ? join(current, function) : this.run("BEGIN DEFERRED", true, function);
    }

    private @Nullable TransactionImpl current() {
        return HANDLE.isBound() && HANDLE.get().database == this ? HANDLE.get() : null;
    }

    private <R> R run(final String begin, final boolean readOnly, final SQLFunction<Transaction, R> function) {
        final TransactionImpl transaction;
        final R result;
        try (final var connection = this.source.getConnection()) {
            transaction = new TransactionImpl(this, connection, readOnly);
            execute(connection, begin);
            try {
                result = ScopedValue.where(HANDLE, transaction).call(() -> function.apply(transaction));
                execute(connection, "COMMIT");
            } catch (final Throwable throwable) {
                try {
                    execute(connection, "ROLLBACK");
                } catch (final SQLException exception) {
                    throwable.addSuppressed(exception);
                }
                runAll(transaction.rollbackActions);
                throw throwable;
            }
        } catch (final SQLException exception) {
            throw new RuntimeException("Failed to run a transaction", exception);
        }
        // Outside the scope, the callbacks open their own transactions
        runAll(transaction.commitActions);
        return result;
    }

    private static <R> R join(final TransactionImpl transaction, final SQLFunction<Transaction, R> function) {
        try {
            return function.apply(transaction);
        } catch (final SQLException exception) {
            throw new RuntimeException("Failed to run a joined transaction", exception);
        }
    }

    private static void execute(final Connection connection, final String statement) throws SQLException {
        try (final var prepared = connection.createStatement()) {
            prepared.execute(statement);
        }
    }

    private static void runAll(final List<Runnable> actions) {
        for (final var action : actions) {
            try {
                action.run();
            } catch (final RuntimeException exception) {
                log.error("A transaction callback failed", exception);
            }
        }
    }

    private static final class TransactionImpl implements Transaction {

        private final SQLiteDatabaseImpl database;
        private final Connection connection;
        private final boolean readOnly;
        private final List<Runnable> commitActions = new ArrayList<>();
        private final List<Runnable> rollbackActions = new ArrayList<>();

        private TransactionImpl(
                final SQLiteDatabaseImpl database, final Connection connection, final boolean readOnly) {
            this.database = database;
            this.connection = connection;
            this.readOnly = readOnly;
        }

        @SuppressWarnings("SqlSourceToSinkFlow")
        @Override
        public StatementBuilder prepareStatement(final @Language("SQL") String statement) throws SQLException {
            return new StatementBuilderImpl(this.connection.prepareStatement(statement));
        }

        @Override
        public void afterCommit(final Runnable action) {
            this.commitActions.add(action);
        }

        @Override
        public void afterRollback(final Runnable action) {
            this.rollbackActions.add(action);
        }
    }

    private static final class StatementBuilderImpl implements StatementBuilder {

        private final PreparedStatement statement;
        private int index = 1;
        private boolean batched = false;

        private StatementBuilderImpl(final PreparedStatement statement) {
            this.statement = statement;
        }

        @Override
        public StatementBuilderImpl bind(final @Nullable String value) throws SQLException {
            if (value == null) {
                this.statement.setNull(this.index, Types.VARCHAR);
            } else {
                this.statement.setString(this.index, value);
            }
            this.index++;
            return this;
        }

        @Override
        public StatementBuilderImpl bind(final int value) throws SQLException {
            this.statement.setInt(this.index, value);
            this.index++;
            return this;
        }

        @Override
        public StatementBuilderImpl bind(final long value) throws SQLException {
            this.statement.setLong(this.index, value);
            this.index++;
            return this;
        }

        @Override
        public StatementBuilderImpl bind(final boolean value) throws SQLException {
            this.statement.setBoolean(this.index, value);
            this.index++;
            return this;
        }

        @Override
        public StatementBuilderImpl bind(final double value) throws SQLException {
            this.statement.setDouble(this.index, value);
            this.index++;
            return this;
        }

        @Override
        public StatementBuilderImpl bind(final @Nullable Double value) throws SQLException {
            if (value == null) {
                this.statement.setNull(this.index, Types.DOUBLE);
                this.index++;
                return this;
            }
            return this.bind(value.doubleValue());
        }

        @Override
        public StatementBuilderImpl bind(final Instant value) throws SQLException {
            return this.bind(value.toEpochMilli());
        }

        @Override
        public StatementBuilderImpl bind(final @Nullable Enum<?> value) throws SQLException {
            return this.bind(value == null ? null : value.name());
        }

        @Override
        public <T> StatementBuilderExecute batch(
                final Iterable<T> values, final SQLBiConsumer<StatementValueBinder<?>, T> consumer)
                throws SQLException {
            this.batched = true;
            for (final var value : values) {
                consumer.accept(this, value);
                this.index = 1;
                this.statement.addBatch();
            }
            return this;
        }

        @Override
        public <T> List<T> executeSelect(final SQLFunction<ResultSet, T> mapper) throws SQLException {
            try (this.statement;
                    final var result = this.statement.executeQuery()) {
                final var list = new ArrayList<T>();
                while (result.next()) {
                    list.add(mapper.apply(result));
                }
                return list;
            }
        }

        @Override
        public <T> Optional<T> executeSingleSelect(final SQLFunction<ResultSet, T> mapper) throws SQLException {
            try (this.statement;
                    final var result = this.statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                final var value = mapper.apply(result);
                if (result.next()) {
                    throw new IllegalStateException("Got more than one result");
                }
                return Optional.of(value);
            }
        }

        @Override
        public int executeUpdate() throws SQLException {
            try (this.statement) {
                return this.batched
                        ? IntStream.of(this.statement.executeBatch()).sum()
                        : this.statement.executeUpdate();
            }
        }

        @Override
        public boolean executeSingleUpdate() throws SQLException {
            final var result = this.executeUpdate();
            return switch (result) {
                case 0 -> false;
                case 1 -> true;
                default -> throw new IllegalStateException("Multiple rows updated, expected 0 or 1, got " + result);
            };
        }
    }
}
