package io.github.headlesshq.web.mods.icon;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Finds the {@code quilt_loader.metadata.icon} declared in a {@code quilt.mod.json},
 * which has the same format as the icon of a {@code fabric.mod.json}.
 *
 * @see FabricIconFinder
 */
public class QuiltIconFinder implements IconFinder {
    @Override
    public Set<String> getPlatforms() {
        return Set.of("quilt");
    }

    @Override
    public Set<String> getEntryNames() {
        return Set.of("quilt.mod.json");
    }

    @Override
    public List<String> findIcons(InputStream inputStream) {
        return FabricIconFinder.icons(
            FabricIconFinder.MAPPER.readTree(inputStream).path("quilt_loader").path("metadata").get("icon")
        );
    }

}
