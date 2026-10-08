package io.github.headlesshq.web.mods.icon;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Finds the {@code logoFile}s declared in the {@code mcmod.info} of old (pre 1.13) Forge mods.
 * The file is either a list of mods, or an object with a {@code modList}.
 */
public class McModInfoIconFinder implements IconFinder {
    private static final JsonMapper MAPPER = new JsonMapper();

    @Override
    public Set<String> getPlatforms() {
        return Set.of("forge");
    }

    @Override
    public Set<String> getEntryNames() {
        return Set.of("mcmod.info");
    }

    @Override
    public List<String> findIcons(InputStream inputStream) {
        JsonNode json = MAPPER.readTree(inputStream);
        List<String> result = new ArrayList<>();
        for (JsonNode mod : json.isArray() ? json : json.path("modList")) {
            JsonNode logo = mod.get("logoFile");
            if (logo != null && logo.isString()) {
                result.add(logo.asString());
            }
        }

        return result;
    }

}
