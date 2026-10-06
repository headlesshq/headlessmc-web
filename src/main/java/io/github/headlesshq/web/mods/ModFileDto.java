package io.github.headlesshq.web.mods;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A mod file installed in a profile.
 *
 * @param path        path relative to the game directory, identifies the file.
 * @param fileName    the name of the file.
 * @param type        the mod type, e.g. {@code mod}, {@code resourcepack}.
 * @param world       the world, for data packs.
 * @param directory   whether this is an unpacked (resource/data/shader) pack.
 * @param enabled     {@code false} if the file has been disabled ({@code .disabled} suffix).
 * @param displayName the name of the (first) mod, or the file name.
 * @param version     the version of the (first) mod, if known.
 * @param mods        the mods contained in the file.
 * @param hasLogo     whether a logo can be fetched for the file.
 */
public record ModFileDto(
    String path,
    String fileName,
    String type,
    @Nullable String world,
    boolean directory,
    long size,
    long modified,
    boolean enabled,
    String displayName,
    @Nullable String version,
    @Nullable String description,
    List<String> authors,
    List<ContainedMod> mods,
    boolean hasLogo
) {
    public record ContainedMod(String id, String name) {
    }

}
