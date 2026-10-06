package io.github.headlesshq.web.mods;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class TestJars {
    /** A tiny but valid 1x1 png. */
    public static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 6,
        0, 0, 0, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89, 0, 0, 0, 0x0A, 'I', 'D', 'A', 'T', 0x78, (byte) 0x9C, 0x63, 0, 1, 0,
        0, 5, 0, 1, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    private TestJars() {
        throw new AssertionError();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, byte[]> entries = new LinkedHashMap<>();

        public Builder text(String name, String content) {
            entries.put(name, content.getBytes(StandardCharsets.UTF_8));
            return this;
        }

        public Builder bytes(String name, byte[] content) {
            entries.put(name, content);
            return this;
        }

        public Path write(Path file) throws IOException {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }

            return file;
        }
    }

    public static Path neoForgeMod(Path file) throws IOException {
        return builder()
            .text("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nImplementation-Version: 2.4.0\n")
            .text("META-INF/neoforge.mods.toml", """
                modLoader="javafml"
                loaderVersion="[4,)"
                [[mods]]
                modId="examplemod"
                version="${file.jarVersion}"
                displayName="Example Mod"
                logoFile="/examplemod_logo.png"
                description='''An example'''
                """)
            .bytes("examplemod_logo.png", PNG)
            .write(file);
    }

}
