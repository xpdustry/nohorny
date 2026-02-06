// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.config;

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
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.AnchorNode;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

public final class SnakeYamlLoader implements ConfigLoader {

    private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    private final SentenceLexer lexer = new PathLexer();

    @Override
    public String name() {
        return "SnakeYamlLoader";
    }

    @Override
    public boolean accepts(final String format) {
        return "yaml".equals(format) || "yml".equals(format);
    }

    @Override
    public GResultOf<List<ConfigNodeContainer>> loadSource(final ConfigSourcePackage sourcePackage)
            throws GestaltException {
        final var source = sourcePackage.getConfigSource();
        if (!source.hasStream()) {
            throw new GestaltException("Config source: " + source.name() + " does not have a stream to load.");
        }

        try (final var stream = source.loadStream()) {
            final var reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
            final var node = this.yaml.compose(reader);
            if (node == null) {
                throw new GestaltException("Exception loading source: " + source.name() + " no yaml found");
            }
            return this.construct(node, "")
                    .mapWithError(result -> List.of(new ConfigNodeContainer(result, source, sourcePackage.getTags())));
        } catch (final IOException | NullPointerException e) {
            throw new GestaltException("Exception loading source: " + source.name(), e);
        }
    }

    private GResultOf<ConfigNode> construct(final Node root, final String path) {
        return switch (root) {
            case SequenceNode node -> {
                final List<ValidationError> errors = new ArrayList<>();
                final List<ConfigNode> array = new ArrayList<>();
                final var values = node.getValue();
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
                yield GResultOf.resultOf(new ArrayNode(array), errors);
            }
            case AnchorNode node -> this.construct(node.getRealNode(), path);
            case MappingNode node -> {
                final List<ValidationError> errors = new ArrayList<>();
                final Map<String, ConfigNode> mapNode = new HashMap<>();
                for (final var pair : node.getValue()) {
                    if (!(pair.getKeyNode() instanceof ScalarNode key)) {
                        errors.add(new ValidationError.UnknownNodeTypeDuringLoad(
                                path, root.getNodeId().name()));
                        continue;
                    }

                    var tokenList = this.tokenizer(key.getValue());
                    tokenList = tokenList.stream().map(this::normalizeSentence).collect(Collectors.toList());
                    final var currentPath = PathUtil.pathForKey(this.lexer, path, tokenList);

                    final var result = this.construct(pair.getValueNode(), currentPath);
                    errors.addAll(result.getErrors());
                    if (!result.hasResults()) {
                        errors.add(new ValidationError.NoResultsFoundForPath(currentPath));
                    } else {
                        ConfigNode currentNode = result.results();
                        for (int index = tokenList.size() - 1; index > 0; index--) {
                            final Map<String, ConfigNode> nextMapNode = new HashMap<>();
                            nextMapNode.put(tokenList.get(index), currentNode);
                            currentNode = new MapNode(nextMapNode);
                        }
                        mapNode.put(tokenList.getFirst(), currentNode);
                    }
                }
                yield GResultOf.resultOf(new MapNode(mapNode), errors);
            }
            case ScalarNode node -> GResultOf.result(new LeafNode(node.getValue()));
            default ->
                GResultOf.errors(new ValidationError.UnknownNodeTypeDuringLoad(
                        path, root.getNodeId().name()));
        };
    }

    private String normalizeSentence(final String sentence) {
        return this.lexer.normalizeSentence(sentence);
    }

    private List<String> tokenizer(final String sentence) {
        return this.lexer.tokenizer(sentence);
    }
}
