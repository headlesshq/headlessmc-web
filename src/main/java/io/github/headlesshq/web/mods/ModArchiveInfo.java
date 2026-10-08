package io.github.headlesshq.web.mods;

import io.github.headlesshq.web.mods.icon.IconFinders;
import io.github.headlesshq.web.mods.icon.PackIconFinder;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * What the web ui shows about a mod file that HeadlessMc's mod readers do not provide:
 * the logo (found by the {@link IconFinders}) and the version.
 *
 * @param logo    path of the logo inside the archive (or directory), if one was found.
 * @param version the version of the (first) mod in the file, if known.
 */
public record ModArchiveInfo(@Nullable String logo, @Nullable String version) {
    static final ModArchiveInfo NONE = new ModArchiveInfo(null, null);

    private static final JsonMapper MAPPER = new JsonMapper();
    private static final Pattern TOML_VERSION = Pattern.compile("(?m)^\\s*version\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern YAML_VERSION = Pattern.compile("(?m)^version\\s*:\\s*['\"]?([^'\"\\s]+)");
    private static final List<String> TOML_FILES = List.of("META-INF/neoforge.mods.toml", "META-INF/mods.toml");

    /**
     * Reads the info of a mod file (jar/zip) or an unpacked pack directory.
     *
     * @param platform the platform of the profile the file belongs to, its icon is preferred.
     */
    public static ModArchiveInfo read(Path file, @Nullable String platform) {
        try {
            if (Files.isDirectory(file)) {
                return Files.isRegularFile(file.resolve(PackIconFinder.PACK_PNG))
                    ? new ModArchiveInfo(PackIconFinder.PACK_PNG, null)
                    : NONE;
            }

            try (ZipFile zip = new ZipFile(file.toFile())) {
                return read(zip, platform);
            }
        } catch (IOException | RuntimeException e) {
            return NONE; // not an archive, or a broken one, the file is still listed
        }
    }

    static ModArchiveInfo read(ZipFile zip, @Nullable String platform) {
        String version;
        try {
            version = version(zip);
        } catch (IOException | RuntimeException e) {
            version = null; // broken metadata, the icon might still be found
        }

        return new ModArchiveInfo(IconFinders.find(zip, platform).orElse(null), version);
    }

    private static @Nullable String version(ZipFile zip) throws IOException {
        String version = null;

        String fabric = text(zip, "fabric.mod.json");
        if (fabric != null) {
            JsonNode json = MAPPER.readTree(fabric);
            version = textValue(json.get("version"));
        }

        String quilt = text(zip, "quilt.mod.json");
        if (quilt != null) {
            JsonNode loader = MAPPER.readTree(quilt).path("quilt_loader");
            version = version != null ? version : textValue(loader.get("version"));
        }

        for (String tomlFile : TOML_FILES) {
            String toml = text(zip, tomlFile);
            if (toml != null) {
                if (version == null) {
                    // the first version after [[mods]], not e.g. the loaderVersion
                    int mods = toml.indexOf("[[mods]]");
                    version = group(TOML_VERSION, mods < 0 ? toml : toml.substring(mods));
                }
            }
        }

        String mcmodInfo = text(zip, "mcmod.info");
        if (mcmodInfo != null) {
            JsonNode json = MAPPER.readTree(mcmodInfo);
            JsonNode first = json.isArray() ? json.path(0) : json.path("modList").path(0);
            version = version != null ? version : textValue(first.get("version"));
        }

        for (String pluginFile : List.of("paper-plugin.yml", "plugin.yml")) {
            String yaml = text(zip, pluginFile);
            if (yaml != null && version == null) {
                version = group(YAML_VERSION, yaml);
            }
        }

        if (version != null && version.contains("${")) {
            // e.g. ${file.jarVersion} on (neo)forge, filled from the manifest
            version = manifestVersion(zip);
        }

        return version;
    }

    /**
     * Opens the logo of a mod file.
     *
     * @return the bytes of the logo, or {@code null} if there is none.
     */
    public static byte @Nullable [] readLogo(Path file, String logo) throws IOException {
        if (Files.isDirectory(file)) {
            Path logoFile = file.resolve(logo).normalize();
            return logoFile.startsWith(file) && Files.isRegularFile(logoFile) ? Files.readAllBytes(logoFile) : null;
        }

        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry entry = zip.getEntry(logo);
            if (entry == null) {
                return null;
            }

            try (InputStream in = zip.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
    }

    private static @Nullable String manifestVersion(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("META-INF/MANIFEST.MF");
        if (entry == null) {
            return null;
        }

        try (InputStream in = zip.getInputStream(entry)) {
            return blankToNull(new Manifest(in).getMainAttributes().getValue("Implementation-Version"));
        }
    }

    private static @Nullable String text(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.getSize() > 1024 * 1024) {
            return null;
        }

        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static @Nullable String group(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static @Nullable String textValue(@Nullable JsonNode node) {
        return node == null || !node.isValueNode() ? null : node.asString();
    }

    private static @Nullable String blankToNull(@Nullable String string) {
        return string == null || string.isBlank() ? null : string;
    }

}
