package io.github.headlesshq.web.mods.icon;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The {@link IconFinder}s for all platforms, ordered for the platform of a profile:
 * a jar can contain metadata for multiple platforms, the icon of the platform the profile runs on is preferred.
 * If no metadata declares an existing icon, commonly used icon paths are tried.
 */
public final class IconFinders {
    public static final List<IconFinder> ALL = List.of(
        new FabricIconFinder(),
        new QuiltIconFinder(),
        new NeoForgeIconFinder(),
        new ForgeIconFinder(),
        new McModInfoIconFinder(),
        new PackIconFinder()
    );

    private static final List<String> FALLBACK_ICONS = List.of(PackIconFinder.PACK_PNG, "logo.png", "icon.png");
    private static final Pattern ASSET_ICON = Pattern.compile("assets/[^/]+/(icon|logo)\\.png");

    private IconFinders() {
        throw new AssertionError();
    }

    /**
     * @param platform the name of the platform, {@code null} if unknown.
     * @return all finders, the ones for the given platform first.
     */
    public static List<IconFinder> forPlatform(@Nullable String platform) {
        if (platform == null) {
            return ALL;
        }

        List<IconFinder> result = new ArrayList<>(ALL.size());
        ALL.stream().filter(finder -> finder.getPlatforms().contains(platform)).forEach(result::add);
        ALL.stream().filter(finder -> !finder.getPlatforms().contains(platform)).forEach(result::add);
        return result;
    }

    /**
     * @return the path of the icon inside the archive.
     */
    public static Optional<String> find(ZipFile zip, @Nullable String platform) {
        return forPlatform(platform).stream()
            .map(finder -> finder.find(zip))
            .flatMap(Optional::stream)
            .findFirst()
            .or(() -> fallback(zip));
    }

    private static Optional<String> fallback(ZipFile zip) {
        Optional<String> icon = FALLBACK_ICONS.stream().filter(name -> zip.getEntry(name) != null).findFirst();
        if (icon.isPresent()) {
            return icon;
        }

        try (Stream<? extends ZipEntry> entries = zip.stream()) {
            return entries.map(ZipEntry::getName).filter(name -> ASSET_ICON.matcher(name).matches()).findFirst();
        }
    }

}
