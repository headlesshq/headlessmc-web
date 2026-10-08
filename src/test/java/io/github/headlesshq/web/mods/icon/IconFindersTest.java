package io.github.headlesshq.web.mods.icon;

import io.github.headlesshq.web.mods.TestJars;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IconFindersTest {
    @TempDir
    Path dir;

    @Test
    void prefersNeoForgeIconFileOverLogoFile() throws IOException {
        Path jar = TestJars.builder()
            .text("META-INF/neoforge.mods.toml", """
                modLoader = "javafml"
                loaderVersion = "[4,)"
                logoFile = "banner.png"

                [[mods]]
                modId = "sodium"
                version = "0.9.3-alpha.1+mc26.3"
                iconFile = "sodium-icon.png" #optional
                """)
            .bytes("banner.png", TestJars.PNG)
            .bytes("sodium-icon.png", TestJars.PNG)
            .write(dir.resolve("sodium.jar"));
        assertEquals(Optional.of("sodium-icon.png"), find(jar, "neoforge"));
    }

    @Test
    void fallsBackToFileLogoOfModsToml() throws IOException {
        Path jar = TestJars.builder()
            .text("META-INF/mods.toml", """
                logoFile = "/logo/forge.png"

                [[mods]]
                modId = "x"
                logoFile = "missing.png"
                """)
            .bytes("logo/forge.png", TestJars.PNG)
            .write(dir.resolve("x.jar"));
        assertEquals(Optional.of("logo/forge.png"), find(jar, "forge"));
    }

    @Test
    void readsMcModInfo() throws IOException {
        Path jar = TestJars.builder()
            .text("mcmod.info", "{\"modListVersion\": 2, \"modList\": [{\"modid\": \"old\", \"logoFile\": \"old.png\"}]}")
            .bytes("old.png", TestJars.PNG)
            .write(dir.resolve("old.jar"));
        assertEquals(Optional.of("old.png"), find(jar, "forge"));
    }

    @Test
    void readsQuiltIcon() throws IOException {
        Path jar = TestJars.builder()
            .text("quilt.mod.json", "{\"quilt_loader\": {\"id\": \"q\", \"metadata\": {\"icon\": \"assets/q/q.png\"}}}")
            .bytes("assets/q/q.png", TestJars.PNG)
            .write(dir.resolve("q.jar"));
        assertEquals(Optional.of("assets/q/q.png"), find(jar, "quilt"));
    }

    @Test
    void toleratesLenientFabricJson() throws IOException {
        Path jar = TestJars.builder()
            .text("fabric.mod.json", """
                {
                  "schemaVersion": 1,
                  "id": "emf",
                  // comments and line breaks in strings are fine for fabric
                  "description": "line
                break",
                  "icon": "assets/emf/icon.png",
                }
                """)
            .bytes("assets/emf/icon.png", TestJars.PNG)
            .write(dir.resolve("emf.jar"));
        assertEquals(Optional.of("assets/emf/icon.png"), find(jar, "fabric"));
    }

    @Test
    void prefersIconOfTheProfilesPlatform() throws IOException {
        Path jar = TestJars.builder()
            .text("fabric.mod.json", "{\"schemaVersion\": 1, \"id\": \"multi\", \"icon\": \"fabric.png\"}")
            .text("META-INF/neoforge.mods.toml", "[[mods]]\nmodId = \"multi\"\nlogoFile = \"neoforge.png\"\n")
            .bytes("fabric.png", TestJars.PNG)
            .bytes("neoforge.png", TestJars.PNG)
            .write(dir.resolve("multi.jar"));
        assertEquals(Optional.of("neoforge.png"), find(jar, "neoforge"));
        assertEquals(Optional.of("fabric.png"), find(jar, "fabric"));
        assertEquals(Optional.of("fabric.png"), find(jar, null));
    }

    @Test
    void ignoresBrokenMetadata() throws IOException {
        Path jar = TestJars.builder()
            .text("META-INF/neoforge.mods.toml", "this is [[ not toml")
            .text("pack.mcmeta", "{\"pack\": {\"pack_format\": 46}}")
            .bytes("pack.png", TestJars.PNG)
            .write(dir.resolve("broken.jar"));
        assertEquals(Optional.of("pack.png"), find(jar, "neoforge"));
    }

    private static Optional<String> find(Path jar, @Nullable String platform) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return IconFinders.find(zip, platform);
        }
    }

}
