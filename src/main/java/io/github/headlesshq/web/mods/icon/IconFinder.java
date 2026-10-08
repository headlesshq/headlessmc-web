package io.github.headlesshq.web.mods.icon;

import io.github.headlesshq.headlessmc.platform.mods.ModReader;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Finds the icon of a mod inside a mod file by reading the metadata of a platform,
 * like HeadlessMc's {@link ModReader}s read the {@link io.github.headlesshq.headlessmc.platform.mods.Mod}s.
 *
 * @see IconFinders
 */
public interface IconFinder {
    long MAX_ENTRY_SIZE = 1024 * 1024;

    /**
     * @return the names of the platforms (see {@link io.github.headlesshq.headlessmc.platform.Platform#getName()})
     * that load the metadata this finder reads, empty if it applies to all platforms (e.g. resource packs).
     */
    Set<String> getPlatforms();

    /**
     * @return the names of the metadata entries to read, in order of preference.
     */
    Set<String> getEntryNames();

    /**
     * Reads the icon paths declared by a metadata entry.
     *
     * @param inputStream the contents of one of the {@link #getEntryNames()}.
     * @return the declared icon paths, relative to the root of the archive, the preferred icon first.
     */
    List<String> findIcons(InputStream inputStream) throws IOException;

    /**
     * @return the path of the first declared icon that actually exists in the given archive.
     */
    default Optional<String> find(ZipFile zip) {
        for (String name : getEntryNames()) {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null || entry.getSize() > MAX_ENTRY_SIZE) {
                continue;
            }

            List<String> icons;
            try (InputStream in = zip.getInputStream(entry)) {
                icons = findIcons(in);
            } catch (IOException | RuntimeException e) {
                continue; // broken metadata, maybe another entry or finder has something
            }

            for (String icon : icons) {
                String path = normalize(icon);
                if (path != null && zip.getEntry(path) != null) {
                    return Optional.of(path);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Normalizes a path inside an archive, metadata sometimes uses e.g. {@code /logo.png} or {@code ./logo.png}.
     */
    static @Nullable String normalize(@Nullable String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        String result = path.trim().replace('\\', '/');
        while (result.startsWith("/") || result.startsWith("./")) {
            result = result.substring(result.startsWith("/") ? 1 : 2);
        }

        return result.isEmpty() ? null : result;
    }

}
