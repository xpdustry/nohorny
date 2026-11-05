// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.mapping;

import com.xpdustry.nohorny.server.persistence.RequesterType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/mappings", produces = MediaType.APPLICATION_JSON_VALUE)
public final class RequesterMappingController {

    private final RequesterMappingService mappings;

    public RequesterMappingController(final RequesterMappingService mappings) {
        this.mappings = mappings;
    }

    @GetMapping
    public List<RequesterMappingView> onList() {
        return this.mappings.list();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public RequesterMappingView onCreate(final @Valid @RequestBody MappingBody body) {
        return this.mappings.create(body.range(), body.type(), body.name());
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RequesterMappingView onUpdate(final @PathVariable String id, final @Valid @RequestBody MappingBody body) {
        return this.mappings.update(id, body.range(), body.type(), body.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void onDelete(final @PathVariable String id) {
        this.mappings.delete(id);
    }

    @PostMapping("/{id}/apply")
    public Applied onApply(final @PathVariable String id) {
        return new Applied(this.mappings.apply(id));
    }

    /// @param name the username of a user or the network name, ignored for the other types
    public record MappingBody(
            @NotNull(message = "range is required") String range,

            @NotNull(message = "type is required") RequesterType type,

            @Nullable String name) {}

    public record Applied(int requests) {}
}
