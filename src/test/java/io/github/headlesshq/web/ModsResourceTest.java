package io.github.headlesshq.web;

import io.github.headlesshq.headlessmc.launcher.profile.Profile;
import io.github.headlesshq.headlessmc.launcher.profile.ProfileService;
import io.github.headlesshq.headlessmc.version.arg.VersionArg;
import io.github.headlesshq.web.mods.TestJars;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ModsResourceTest {
    private static final String PROFILE = "web-mods-test";

    @Inject
    ProfileService profileService;

    private Path gameDir;

    @BeforeEach
    void setup() throws IOException {
        gameDir = Path.of("build", "test-hmc", "games", PROFILE).toAbsolutePath();
        if (Files.exists(gameDir)) {
            try (Stream<Path> walk = Files.walk(gameDir)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }

        Files.createDirectories(gameDir.resolve("saves").resolve("My World"));
        profileService.save(new Profile(PROFILE, VersionArg.parse("neoforge", "26.3"), gameDir));
    }

    @Test
    void listsUploadsTogglesAndDeletesMods() throws IOException {
        given().get("/api/mods/" + PROFILE).then().statusCode(200)
            .body("type", is("mod"))
            .body("types", hasItems("mod", "resourcepack", "shader", "datapack"))
            .body("worlds", hasItem("My World"))
            .body("files", empty());

        Path jar = TestJars.neoForgeMod(Files.createTempDirectory("mods").resolve("example-2.4.0.jar"));
        given().multiPart("files", jar.toFile(), "application/java-archive")
            .post("/api/mods/" + PROFILE + "/files?type=mod")
            .then().statusCode(200)
            .body("[0].path", is("mods/example-2.4.0.jar"));
        assertTrue(Files.isRegularFile(gameDir.resolve("mods/example-2.4.0.jar")));

        // adding it again does not silently overwrite it
        given().multiPart("files", jar.toFile(), "application/java-archive")
            .post("/api/mods/" + PROFILE + "/files?type=mod")
            .then().statusCode(409);

        given().get("/api/mods/" + PROFILE + "?type=mod").then().statusCode(200)
            .body("files[0].displayName", is("Example Mod"))
            .body("files[0].version", is("2.4.0"))
            .body("files[0].hasLogo", is(true))
            .body("files[0].enabled", is(true));

        byte[] logo = given().queryParam("path", "mods/example-2.4.0.jar").get("/api/mods/" + PROFILE + "/logo")
            .then().statusCode(200).contentType("image/png").extract().asByteArray();
        assertArrayEquals(TestJars.PNG, logo);

        given().queryParam("path", "mods/example-2.4.0.jar").queryParam("enabled", false)
            .post("/api/mods/" + PROFILE + "/files/enabled")
            .then().statusCode(200)
            .body("path", is("mods/example-2.4.0.jar.disabled"))
            .body("enabled", is(false));
        assertTrue(Files.isRegularFile(gameDir.resolve("mods/example-2.4.0.jar.disabled")));

        given().queryParam("path", "mods/example-2.4.0.jar.disabled").delete("/api/mods/" + PROFILE + "/files")
            .then().statusCode(204);
        assertTrue(Files.notExists(gameDir.resolve("mods/example-2.4.0.jar.disabled")));
    }

    @Test
    void addsDataPacksToWorlds() throws IOException {
        Path pack = TestJars.builder().text("pack.mcmeta", "{}").bytes("pack.png", TestJars.PNG)
            .write(Files.createTempDirectory("packs").resolve("pack.zip"));
        given().multiPart("files", pack.toFile(), "application/zip")
            .post("/api/mods/" + PROFILE + "/files?type=datapack&world=My World")
            .then().statusCode(200)
            .body("[0].path", is("saves/My World/datapacks/pack.zip"))
            .body("[0].world", is("My World"))
            .body("[0].hasLogo", is(true));
    }

    @Test
    void rejectsFilesOutsideOfModDirectories() throws IOException {
        Files.writeString(gameDir.resolve("options.txt"), "fov:1");
        given().queryParam("path", "options.txt").delete("/api/mods/" + PROFILE + "/files").then().statusCode(400);
        given().queryParam("path", "../../config.properties").delete("/api/mods/" + PROFILE + "/files").then().statusCode(400);
        assertTrue(Files.exists(gameDir.resolve("options.txt")));

        Path text = Files.writeString(Files.createTempDirectory("x").resolve("notes.txt"), "hi");
        given().multiPart("files", text.toFile(), "text/plain")
            .post("/api/mods/" + PROFILE + "/files?type=mod")
            .then().statusCode(400);
    }

    @Test
    void unknownProfile() {
        given().get("/api/mods/does-not-exist").then().statusCode(404);
    }

}
