package io.github.headlesshq.web.mods;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModArchiveInfoTest {
    @TempDir
    Path dir;

    @Test
    void readsNeoForgeLogoAndManifestVersion() throws IOException {
        Path jar = TestJars.neoForgeMod(dir.resolve("example.jar"));
        ModArchiveInfo info = ModArchiveInfo.read(jar);
        assertEquals("examplemod_logo.png", info.logo());
        assertEquals("2.4.0", info.version());
        assertArrayEquals(TestJars.PNG, ModArchiveInfo.readLogo(jar, info.logo()));
    }

    @Test
    void readsLargestFabricIcon() throws IOException {
        Path jar = TestJars.builder()
            .text("fabric.mod.json", """
                {"schemaVersion": 1, "id": "sodium", "version": "0.9.2",
                 "icon": {"16": "assets/sodium/small.png", "128": "assets/sodium/large.png"}}
                """)
            .bytes("assets/sodium/small.png", TestJars.PNG)
            .bytes("assets/sodium/large.png", TestJars.PNG)
            .write(dir.resolve("sodium.jar"));
        assertEquals(new ModArchiveInfo("assets/sodium/large.png", "0.9.2"), ModArchiveInfo.read(jar));
    }

    @Test
    void readsPackPng() throws IOException {
        Path pack = TestJars.builder()
            .text("pack.mcmeta", "{\"pack\": {\"pack_format\": 46}}")
            .bytes("pack.png", TestJars.PNG)
            .write(dir.resolve("pack.zip"));
        assertEquals("pack.png", ModArchiveInfo.read(pack).logo());

        Path folder = Files.createDirectories(dir.resolve("folder-pack"));
        Files.write(folder.resolve("pack.png"), TestJars.PNG);
        assertEquals("pack.png", ModArchiveInfo.read(folder).logo());
    }

    @Test
    void fallsBackToAssetIcons() throws IOException {
        Path jar = TestJars.builder()
            .text("META-INF/mods.toml", "[[mods]]\nmodId=\"x\"\nversion=\"1.0\"\nlogoFile=\"missing.png\"\n")
            .bytes("assets/x/icon.png", TestJars.PNG)
            .write(dir.resolve("x.jar"));
        assertEquals(new ModArchiveInfo("assets/x/icon.png", "1.0"), ModArchiveInfo.read(jar));
    }

    @Test
    void toleratesBrokenFiles() throws IOException {
        Path file = Files.writeString(dir.resolve("broken.jar"), "not a zip");
        ModArchiveInfo info = ModArchiveInfo.read(file);
        assertNull(info.logo());
        assertNull(info.version());
    }

}
