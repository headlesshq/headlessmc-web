package io.github.headlesshq.web.mods.icon;

import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.toml.TomlMapper;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds icons declared in a Forge {@code mods.toml}. FML reads the {@code logoFile} of a {@code [[mods]]} entry,
 * falling back to the {@code logoFile} of the file. Some mods also declare an {@code iconFile} (e.g. Sodium),
 * which is preferred, logos are often wide banners, icons are square:
 * <pre>
 * {@code
 * logoFile = "banner.png"
 *
 * [[mods]]
 * modId = "sodium"
 * iconFile = "sodium-icon.png"
 * }
 * </pre>
 */
public class ForgeIconFinder implements IconFinder {
    private static final TomlMapper MAPPER = TomlMapper.builder().build();

    private final Set<String> platforms;
    private final Set<String> entryNames;

    public ForgeIconFinder() {
        // forge.mods.toml is what HeadlessMc's ForgeModsTomlReader reads
        this(Set.of("forge"), new LinkedHashSet<>(List.of("META-INF/mods.toml", "META-INF/forge.mods.toml")));
    }

    protected ForgeIconFinder(Set<String> platforms, Set<String> entryNames) {
        this.platforms = platforms;
        this.entryNames = entryNames;
    }

    @Override
    public Set<String> getPlatforms() {
        return platforms;
    }

    @Override
    public Set<String> getEntryNames() {
        return entryNames;
    }

    @Override
    public List<String> findIcons(InputStream inputStream) {
        JsonNode toml = MAPPER.readTree(inputStream);
        List<String> result = new ArrayList<>();
        JsonNode mods = toml.path("mods");
        for (JsonNode mod : mods.isArray() ? mods : List.of(mods)) {
            add(result, mod.get("iconFile"));
            add(result, mod.get("logoFile"));
        }

        add(result, toml.get("iconFile"));
        add(result, toml.get("logoFile"));
        return result;
    }

    private static void add(List<String> result, @Nullable JsonNode node) {
        if (node != null && node.isString()) {
            result.add(node.asString());
        }
    }

}
