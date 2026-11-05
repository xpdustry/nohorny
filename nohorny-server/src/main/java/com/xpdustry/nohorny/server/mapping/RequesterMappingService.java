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

@Service
public class RequesterMappingService {

    private static final Logger log = LoggerFactory.getLogger(RequesterMappingService.class);

    public static final int MAX_NETWORK_LENGTH = 64;

    private final RequesterMappingRepository mappings;
    private final UserAccountRepository users;
    private final ClassificationRequestRepository requests;
    private final DailyStatRepository dailyStats;

    private volatile RuleSet rules = new RuleSet(List.of());

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

    public Optional<Requester> find(final InetAddress address) {
        return this.rules.match(address).map(Rule::requester);
    }

    public List<RequesterMappingView> list() {
        return this.mappings.findAll().stream()
                .sorted(Comparator.comparing(RequesterMapping::getRange, RANGE_ORDER))
                .map(RequesterMappingService::toView)
                .toList();
    }

    @Transactional
    public RequesterMappingView create(final String range, final RequesterType type, final @Nullable String name) {
        final var parsed = parseRange(range);
        final var requester = this.requester(type, name);
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
            final String id, final String range, final RequesterType type, final @Nullable String name) {
        final var mapping = this.find(id);
        final var parsed = parseRange(range);
        final var requester = this.requester(type, name);
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

    @Transactional
    public void deleteForUser(final String username) {
        if (this.mappings.deleteByUser(username) > 0) {
            log.info("Deleted the mappings to user {}", username);
            this.reloadAfterCommit();
        }
    }

    /// The statistics of the deleted requests stay, their addresses are gone.
    @Transactional
    public int apply(final String id) {
        final var mapping = this.find(id);
        final var range = mapping.getRange();
        final var requester = mapping.getRequester();
        final var rules = rulesOf(this.mappings.findRows());
        final var addresses = this.requests.findUnauthenticatedAddresses().stream()
                .filter(value -> {
                    final var address = AddressRange.parseRemoteAddress(value);
                    return address != null
                            && range.contains(address)
                            && rules.match(address)
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

    private Requester requester(final RequesterType type, final @Nullable String name) {
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

    private static RuleSet rulesOf(final List<RequesterMappingRepository.Row> rows) {
        return new RuleSet(rows.stream()
                .map(row -> new Rule(
                        row.getId(),
                        AddressRange.parse(row.getRange()),
                        new Requester(RequesterType.valueOf(row.getRequesterType()), row.getRequesterName())))
                .toList());
    }

    private static RequesterMappingView toView(final RequesterMapping mapping) {
        final var requester = mapping.getRequester();
        return new RequesterMappingView(
                mapping.getId(),
                mapping.getRange().toString(),
                requester.type(),
                requester.name(),
                mapping.getCreatedAt());
    }

    private static String describe(final Requester requester) {
        return requester.name() == null
                ? requester.type().key()
                : requester.type().key() + " " + requester.name();
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "address range already mapped");
    }

    private static final Comparator<AddressRange> RANGE_ORDER = Comparator.comparingInt(
                    (AddressRange range) -> range.network().getAddress().length)
            .thenComparing(range -> range.network().getAddress(), Arrays::compareUnsigned)
            .thenComparingInt(AddressRange::prefix);

    private record Rule(String id, AddressRange range, Requester requester) {}

    /// The rules, the most specific range first.
    private record RuleSet(List<Rule> rules) {

        private RuleSet {
            rules = rules.stream()
                    .sorted(Comparator.comparingInt((Rule rule) -> rule.range().prefix())
                            .reversed())
                    .toList();
        }

        Optional<Rule> match(final InetAddress address) {
            return this.rules.stream()
                    .filter(rule -> rule.range().contains(address))
                    .findFirst();
        }
    }
}
