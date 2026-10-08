package io.github.headlesshq.web.mods.icon;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Resource and data packs (and mods that are packs too) have their icon at {@code pack.png}, next to the {@code pack.mcmeta}.
 */
public class PackIconFinder implements IconFinder {
    public static final String PACK_PNG = "pack.png";

    @Override
    public Set<String> getPlatforms() {
        return Set.of();
    }

    @Override
    public Set<String> getEntryNames() {
        return Set.of("pack.mcmeta");
    }

    @Override
    public List<String> findIcons(InputStream inputStream) {
        return List.of(PACK_PNG);
    }

}
