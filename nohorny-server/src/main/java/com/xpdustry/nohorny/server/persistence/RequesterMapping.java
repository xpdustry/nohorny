// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.server.mapping.AddressRange;
import java.time.Instant;

/// Stored in `requester_mapping`, the range in its normalized CIDR notation, see [RequesterMappingRepository].
public record RequesterMapping(String id, AddressRange range, Requester requester, Instant createdAt) {}
