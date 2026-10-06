package io.github.headlesshq.web.mods;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * What the web ui shows about a mod file that HeadlessMc's mod readers do not provide:
 * the logo (from the mod metadata, e.g. {@code logoFile} in neoforge.mods.toml or {@code icon} in fabric.mod.json,
 * or {@code pack.png} of resource/data packs) and the version.
 *
 * @param logo    path of the logo inside the archive (or directory), if one was found.
 * @param version the version of the (first) mod in the file, if known.
 */
public record ModArchiveInfo(@Nullable String logo, @Nullable String version) {
    static final ModArchiveInfo NONE = new ModArchiveInfo(null, null);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern TOML_LOGO = Pattern.compile("(?m)^\\s*logoFile\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern TOML_VERSION = Pattern.compile("(?m)^\\s*version\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern YAML_VERSION = Pattern.compile("(?m)^version\\s*:\\s*['\"]?([^'\"\\s]+)");
    private static final List<String> TOML_FILES = List.of("META-INF/neoforge.mods.toml", "META-INF/mods.toml");
    private static final List<String> FALLBACK_LOGOS = List.of("pack.png", "logo.png", "icon.png");
    private static final Pattern ASSET_ICON = Pattern.compile("assets/[^/]+/(icon|logo)\\.png");

    /**
     * Reads the info of a mod file (jar/zip) or an unpacked pack directory.
     */
    public static ModArchiveInfo read(Path file) {
        try {
            if (Files.isDirectory(file)) {
                return Files.isRegularFile(file.resolve("pack.png")) ? new ModArchiveInfo("pack.png", null) : NONE;
            }

            try (ZipFile zip = new ZipFile(file.toFile())) {
                return read(zip);
            }
        } catch (IOException | RuntimeException e) {
            return NONE; // not an archive, or a broken one, the file is still listed
        }
    }

    static ModArchiveInfo read(ZipFile zip) throws IOException {
        String logo = null;
        String version = null;

        String fabric = text(zip, "fabric.mod.json");
        if (fabric != null) {
            JsonNode json = MAPPER.readTree(fabric);
            logo = icon(json.get("icon"));
            version = textValue(json.get("version"));
        }

        String quilt = text(zip, "quilt.mod.json");
        if (quilt != null) {
            JsonNode loader = MAPPER.readTree(quilt).path("quilt_loader");
            logo = logo != null ? logo : icon(loader.path("metadata").get("icon"));
            version = version != null ? version : textValue(loader.get("version"));
        }

        for (String tomlFile : TOML_FILES) {
            String toml = text(zip, tomlFile);
            if (toml != null) {
                logo = logo != null ? logo : group(TOML_LOGO, toml);
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
            logo = logo != null ? logo : blankToNull(textValue(first.get("logoFile")));
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

        logo = normalize(logo);
        if (logo == null || zip.getEntry(logo) == null) {
            logo = fallbackLogo(zip);
        }

        return new ModArchiveInfo(logo, version);
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

    private static @Nullable String icon(@Nullable JsonNode icon) {
        if (icon == null || icon.isNull()) {
            return null;
        }

        if (icon.isTextual()) {
            return icon.asText();
        }

        // {"16": "assets/mod/icon16.png", "128": "assets/mod/icon128.png"}, use the largest
        List<Map.Entry<String, JsonNode>> sizes = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = icon.fields(); it.hasNext(); ) {
            sizes.add(it.next());
        }

        return sizes.stream()
            .max(Comparator.comparingInt(entry -> parseInt(entry.getKey())))
            .map(entry -> entry.getValue().asText())
            .orElse(null);
    }

    private static @Nullable String fallbackLogo(ZipFile zip) {
        for (String candidate : FALLBACK_LOGOS) {
            if (zip.getEntry(candidate) != null) {
                return candidate;
            }
        }

        return zip.stream()
            .map(ZipEntry::getName)
            .filter(name -> ASSET_ICON.matcher(name).matches())
            .findFirst()
            .orElse(null);
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

    private static @Nullable String normalize(@Nullable String logo) {
        if (logo == null || logo.isBlank()) {
            return null;
        }

        String result = logo.replace('\\', '/');
        while (result.startsWith("/") || result.startsWith("./")) {
            result = result.substring(result.startsWith("/") ? 1 : 2);
        }

        return result;
    }

    private static @Nullable String group(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static @Nullable String textValue(@Nullable JsonNode node) {
        return node == null || !node.isValueNode() ? null : node.asText();
    }

    private static @Nullable String blankToNull(@Nullable String string) {
        return string == null || string.isBlank() ? null : string;
    }

    private static int parseInt(String string) {
        try {
            return Integer.parseInt(string.toLowerCase(Locale.ROOT).replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

}
