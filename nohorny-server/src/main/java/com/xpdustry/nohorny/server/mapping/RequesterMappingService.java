// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.mapping;

import com.xpdustry.nohorny.server.persistence.ClassificationRequestRepository;
import com.xpdustry.nohorny.server.persistence.DailyStatRepository;
import com.xpdustry.nohorny.server.persistence.Requester;
import com.xpdustry.nohorny.server.persistence.RequesterMapping;
import com.xpdustry.nohorny.server.persistence.RequesterMappingRepository;
import com.xpdustry.nohorny.server.persistence.RequesterType;
import com.xpdustry.nohorny.server.persistence.UserAccountRepository;
import com.xpdustry.nohorny.server.persistence.UuidV7;
import java.net.InetAddress;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/// Manages the requester mappings, and finds the one of a client address.
///
/// The mappings are read on every classification, so they are kept in memory and reloaded once a change commits.
@Service
public class RequesterMappingService {

    private static final Logger log = LoggerFactory.getLogger(RequesterMappingService.class);

    /// The longest network name of a mapping, the listed ones are far shorter.
    public static final int MAX_NETWORK_LENGTH = 64;

    private final RequesterMappingRepository mappings;
    private final UserAccountRepository users;
    private final ClassificationRequestRepository requests;
    private final DailyStatRepository dailyStats;

    /// The mappings, the most specific range first
    private volatile List<Rule> rules = List.of();

    public RequesterMappingService(
            final RequesterMappingRepository mappings,
            final UserAccountRepository users,
            final ClassificationRequestRepository requests,
            final DailyStatRepository dailyStats) {
        this.mappings = mappings;
        this.users = users;
        this.requests = requests;
        this.dailyStats = dailyStats;
        this.reload();
    }

    /// @return the requester of the most specific mapping containing the address, empty if none does
    public Optional<Requester> find(final InetAddress address) {
        return this.match(address).map(Rule::requester);
    }

    /// @return the mappings sorted by range, the IPv4 ones first
    public List<RequesterMappingView> list() {
        return this.mappings.findAll().stream()
                .map(RequesterMappingService::toView)
                .sorted(Comparator.comparing(RequesterMappingView::range, RANGE_ORDER))
                .toList();
    }

    @Transactional
    public RequesterMappingView create(final String range, final String type, final @Nullable String name) {
        final var parsed = parseRange(range).toString();
        final var requester = this.parseRequester(type, name);
        if (this.mappings.existsByRange(parsed)) {
            throw conflict();
        }
        final var mapping = this.mappings.save(new RequesterMapping(
                UuidV7.next().toString(), parsed, requester, Instant.now().truncatedTo(ChronoUnit.MILLIS)));
        log.info("Mapped {} to {}", parsed, describe(requester));
        this.reloadAfterCommit();
        return toView(mapping);
    }

    @Transactional
    public RequesterMappingView update(
            final String id, final String range, final String type, final @Nullable String name) {
        final var mapping = this.find(id);
        final var parsed = parseRange(range).toString();
        final var requester = this.parseRequester(type, name);
        if (!parsed.equals(mapping.getRange()) && this.mappings.existsByRange(parsed)) {
            throw conflict();
        }
        mapping.update(parsed, requester);
        log.info("Changed mapping {} to {} -> {}", id, parsed, describe(requester));
        this.reloadAfterCommit();
        return toView(mapping);
    }

    @Transactional
    public void delete(final String id) {
        final var mapping = this.find(id);
        this.mappings.delete(mapping);
        log.info("Deleted the mapping of {}", mapping.getRange());
        this.reloadAfterCommit();
    }

    /// Deletes the mappings to an account, within the transaction deleting it.
    @Transactional
    public void deleteForUser(final String username) {
        if (this.mappings.deleteByUser(username) > 0) {
            log.info("Deleted the mappings to user {}", username);
            this.reloadAfterCommit();
        }
    }

    /// Gives the requester of a mapping to the retained requests it would have matched, and moves their daily
    /// statistics with them. The requests sent with an account are kept, like the addresses a more specific mapping
    /// contains. The statistics of the deleted requests stay, their addresses are gone.
    ///
    /// @return the number of changed requests
    @Transactional
    public int apply(final String id) {
        final var mapping = this.find(id);
        final var range = parseRange(mapping.getRange());
        final var requester = mapping.getRequester();
        final var rules = rulesOf(this.mappings.findRows());
        final var addresses = this.requests.findUnauthenticatedAddresses().stream()
                .filter(value -> {
                    final var address = AddressRange.parseRemoteAddress(value);
                    return address != null
                            && range.contains(address)
                            && match(rules, address)
                                    .map(rule -> rule.id().equals(id))
                                    .orElse(false);
                })
                .toList();
        if (addresses.isEmpty()) {
            return 0;
        }
        final var type = requester.type().name();
        this.dailyStats.move(this.requests.countRelabeled(addresses, type, requester.name()), requester);
        final var changed = this.requests.relabel(addresses, type, requester.name());
        log.info("Applied the mapping of {} to {} requests", mapping.getRange(), changed);
        return changed;
    }

    private Optional<Rule> match(final InetAddress address) {
        return match(this.rules, address);
    }

    private static Optional<Rule> match(final List<Rule> rules, final InetAddress address) {
        for (final var rule : rules) {
            if (rule.range().contains(address)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    /// Synchronized, so two reloads cannot publish out of order: the last one reads every committed change.
    private synchronized void reload() {
        this.rules = rulesOf(this.mappings.findRows());
    }

    /// The reads run on other connections, they only see the change once committed.
    private void reloadAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                RequesterMappingService.this.reload();
            }
        });
    }

    private RequesterMapping find(final String id) {
        return this.mappings
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "mapping not found"));
    }

    private Requester parseRequester(final String key, final @Nullable String name) {
        final var type = RequesterType.fromKey(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid requester type"));
        final var trimmed = name == null ? "" : name.strip();
        return switch (type) {
            case USER -> {
                if (!this.users.existsById(trimmed)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "user not found");
                }
                yield Requester.user(trimmed);
            }
            case MINDUSTRY_NETWORK -> {
                if (trimmed.isEmpty() || trimmed.length() > MAX_NETWORK_LENGTH) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "network name must be 1 to " + MAX_NETWORK_LENGTH + " characters");
                }
                yield Requester.mindustryNetwork(trimmed);
            }
            case LOCAL -> Requester.local();
            case ANONYMOUS -> Requester.anonymous();
        };
    }

    private static AddressRange parseRange(final String value) {
        try {
            return AddressRange.parse(value);
        } catch (final IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "invalid address range, " + exception.getMessage());
        }
    }

    private static List<Rule> rulesOf(final List<RequesterMappingRepository.Row> rows) {
        return rows.stream()
                .map(row -> new Rule(
                        row.getId(),
                        parseRange(row.getRange()),
                        new Requester(RequesterType.valueOf(row.getRequesterType()), row.getRequesterName())))
                .sorted(Comparator.comparingInt((Rule rule) -> rule.range().prefix())
                        .reversed())
                .toList();
    }

    private static RequesterMappingView toView(final RequesterMapping mapping) {
        final var requester = mapping.getRequester();
        return new RequesterMappingView(
                mapping.getId(), mapping.getRange(), requester.type().key(), requester.name(), mapping.getCreatedAt());
    }

    private static String describe(final Requester requester) {
        return requester.name() == null
                ? requester.type().key()
                : requester.type().key() + " " + requester.name();
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "address range already mapped");
    }

    /// The IPv4 ranges first, then by first address and prefix.
    private static final Comparator<String> RANGE_ORDER = Comparator.comparing(
            (String value) -> AddressRange.parse(value),
            Comparator.comparingInt((AddressRange range) -> range.network().getAddress().length)
                    .thenComparing(range -> range.network().getAddress(), Arrays::compareUnsigned)
                    .thenComparingInt(AddressRange::prefix));

    private record Rule(String id, AddressRange range, Requester requester) {}
}
