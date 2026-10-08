package io.github.headlesshq.web.mods.icon;

import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the {@code icon} declared in a {@code fabric.mod.json}.
 * The icon is either a path, or a map from the width of the icon to its path, in which case the largest is preferred:
 * <pre>
 * {@code
 * "icon": {"16": "assets/mod/icon16.png", "128": "assets/mod/icon128.png"}
 * }
 * </pre>
 *
 * @see <a href=https://wiki.fabricmc.net/documentation:fabric_mod_json_spec>fabric.mod.json spec</a>
 */
public class FabricIconFinder implements IconFinder {
    // fabric loader is lenient, e.g. line breaks in strings and comments are fine
    static final JsonMapper MAPPER = JsonMapper.builder()
        .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
        .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .build();

    @Override
    public Set<String> getPlatforms() {
        return Set.of("fabric", "quilt");
    }

    @Override
    public Set<String> getEntryNames() {
        return Set.of("fabric.mod.json");
    }

    @Override
    public List<String> findIcons(InputStream inputStream) {
        return icons(MAPPER.readTree(inputStream).get("icon"));
    }

    /**
     * @return the paths of an icon that is either a path or a map of widths to paths, the largest first.
     */
    static List<String> icons(@Nullable JsonNode icon) {
        if (icon == null) {
            return List.of();
        }

        if (icon.isString()) {
            return List.of(icon.asString());
        }

        List<Map.Entry<String, JsonNode>> sizes = new ArrayList<>();
        sizes.addAll(icon.properties());
        return sizes.stream()
            .filter(entry -> entry.getValue().isString())
            .sorted(Comparator.comparingInt((Map.Entry<String, JsonNode> entry) -> width(entry.getKey())).reversed())
            .map(entry -> entry.getValue().asString())
            .toList();
    }

    private static int width(String key) {
        try {
            return Integer.parseInt(key.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

}
