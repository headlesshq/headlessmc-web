package io.github.headlesshq.web.mods;

import io.github.headlesshq.headlessmc.mods.distribution.RemoteMod;
import org.jspecify.annotations.Nullable;

/**
 * A {@link RemoteMod} found by a search, with its icon.
 *
 * @param iconUrl url of the icon on the distribution platform, if it has one.
 */
public record RemoteModDto(String id, String name, String description, @Nullable String iconUrl) {
    public static RemoteModDto of(RemoteMod mod, @Nullable String iconUrl) {
        return new RemoteModDto(mod.id(), mod.name(), mod.description(), iconUrl);
    }

}
