// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// Gives the clients of an address range another requester when they send no account, such as the Mindustry servers
/// of a Docker network to the network they belong to. The most specific range wins.
@Entity
@Table(name = "requester_mapping")
public class RequesterMapping extends AssignedIdEntity<String> {

    @Id
    private String id;

    @Column(name = "address_range")
    private String range;

    @Enumerated(EnumType.STRING)
    private RequesterType requesterType;

    private @Nullable String requesterName;

    @Convert(converter = EpochMillisConverter.class)
    private Instant createdAt;

    @SuppressWarnings("NullAway")
    protected RequesterMapping() {}

    /// @param id the UUIDv7 identifier
    /// @param range the normalized CIDR notation of the range, see `AddressRange`
    /// @param requester the requester the clients become, a Mindustry network is named
    public RequesterMapping(final String id, final String range, final Requester requester, final Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.range = range;
        this.requesterType = requester.type();
        this.requesterName = requester.name();
    }

    @Override
    public String getId() {
        return this.id;
    }

    public String getRange() {
        return this.range;
    }

    public Requester getRequester() {
        return new Requester(this.requesterType, this.requesterName);
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public void update(final String range, final Requester requester) {
        this.range = range;
        this.requesterType = requester.type();
        this.requesterName = requester.name();
    }
}
