package io.github.headlesshq.web.mods.icon;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds icons declared in a {@code neoforge.mods.toml}, which has the same format as Forge's {@code mods.toml}.
 * NeoForge read {@code META-INF/mods.toml} before 20.5, so that is checked too.
 *
 * @see ForgeIconFinder
 */
public class NeoForgeIconFinder extends ForgeIconFinder {
    public NeoForgeIconFinder() {
        super(Set.of("neoforge"), new LinkedHashSet<>(List.of("META-INF/neoforge.mods.toml", "META-INF/mods.toml")));
    }

}
