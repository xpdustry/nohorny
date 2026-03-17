// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.github.gestalt.config.entity.ConfigNodeContainer;
import org.github.gestalt.config.entity.ValidationError;
import org.github.gestalt.config.exceptions.GestaltException;
import org.github.gestalt.config.lexer.PathLexer;
import org.github.gestalt.config.lexer.SentenceLexer;
import org.github.gestalt.config.loader.ConfigLoader;
import org.github.gestalt.config.node.ArrayNode;
import org.github.gestalt.config.node.ConfigNode;
import org.github.gestalt.config.node.LeafNode;
import org.github.gestalt.config.node.MapNode;
import org.github.gestalt.config.source.ConfigSourcePackage;
import org.github.gestalt.config.utils.GResultOf;
import org.github.gestalt.config.utils.PathUtil;

// Certified Codex Slop :)
public final class GsonJsonLoader implements ConfigLoader {

    private final SentenceLexer lexer = new PathLexer();

    @Override
    public String name() {
        return "GsonJsonLoader";
    }

    @Override
    public boolean accepts(final String format) {
        return "json".equals(format);
    }

    @Override
    public GResultOf<List<ConfigNodeContainer>> loadSource(final ConfigSourcePackage sourcePackage)
            throws GestaltException {
        final var source = sourcePackage.getConfigSource();
        if (!source.hasStream()) {
            throw new GestaltException("Config source: " + source.name() + " does not have a stream to load.");
        }

        try (final var stream = source.loadStream()) {
            final var reader = new JsonReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            reader.setStrictness(Strictness.STRICT);

            final var element = JsonParser.parseReader(reader);
            if (element == null || element.isJsonNull()) {
                throw new GestaltException("Exception loading source: " + source.name() + " no json found");
            }

            return this.construct(element, "")
                    .mapWithError(result -> List.of(new ConfigNodeContainer(result, source, sourcePackage.getTags())));
        } catch (final IOException | JsonParseException e) {
            throw new GestaltException("Exception loading source: " + source.name(), e);
        }
    }

    private GResultOf<ConfigNode> construct(final JsonElement root, final String path) {
        if (root.isJsonArray()) {
            final List<ValidationError> errors = new ArrayList<>();
            final List<ConfigNode> array = new ArrayList<>();
            final var values = root.getAsJsonArray();
            for (int index = 0; index < values.size(); index++) {
                final var currentPath = PathUtil.pathForIndex(this.lexer, path, index);
                final var result = this.construct(values.get(index), currentPath);
                errors.addAll(result.getErrors());
                if (!result.hasResults()) {
                    errors.add(new ValidationError.NoResultsFoundForPath(currentPath));
                } else {
                    array.add(result.results());
                }
            }
            return GResultOf.resultOf(new ArrayNode(array), errors);
        }

        if (root.isJsonObject()) {
            final List<ValidationError> errors = new ArrayList<>();
            final Map<String, ConfigNode> mapNode = new HashMap<>();
            for (final var entry : root.getAsJsonObject().entrySet()) {
                final var tokens = this.lexer.tokenizer(entry.getKey()).stream()
                        .map(this.lexer::normalizeSentence)
                        .collect(Collectors.toList());
                final var currentPath = PathUtil.pathForKey(this.lexer, path, tokens);

                final var result = this.construct(entry.getValue(), currentPath);
                errors.addAll(result.getErrors());
                if (!result.hasResults()) {
                    errors.add(new ValidationError.NoResultsFoundForPath(currentPath));
                } else {
                    ConfigNode currentNode = result.results();
                    for (int index = tokens.size() - 1; index > 0; index--) {
                        final Map<String, ConfigNode> nextMapNode = new HashMap<>();
                        nextMapNode.put(tokens.get(index), currentNode);
                        currentNode = new MapNode(nextMapNode);
                    }
                    mapNode.put(tokens.getFirst(), currentNode);
                }
            }
            return GResultOf.resultOf(new MapNode(mapNode), errors);
        }

        if (root.isJsonNull()) {
            return GResultOf.result(new LeafNode(null));
        }

        if (root.isJsonPrimitive()) {
            return GResultOf.result(new LeafNode(root.getAsJsonPrimitive().getAsString()));
        }

        return GResultOf.errors(new ValidationError.UnknownNodeTypeDuringLoad(
                path, root.getClass().getSimpleName()));
    }
}
