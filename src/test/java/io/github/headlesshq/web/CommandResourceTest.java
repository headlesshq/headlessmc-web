package io.github.headlesshq.web;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class CommandResourceTest {
    @Test
    void describesTheCommandTree() {
        given().get("/api/commands")
            .then()
            .statusCode(200)
            .body("name", org.hamcrest.Matchers.is("headlessmc"))
            .body("subcommands.name", hasItems("account", "config", "java", "launch", "version", "profile", "server", "mod"))
            .body("subcommands.find { it.name == 'account' }.options.names.flatten()", hasItem("--provider"))
            .body("subcommands.find { it.name == 'account' }.subcommands.name", hasItems("login", "list", "remove", "select", "refresh"))
            // standard help options are not part of the forms
            .body("subcommands.find { it.name == 'config' }.options.names.flatten()", not(hasItem("--help")));
    }

    @Test
    void completesSubcommands() {
        given().contentType(ContentType.JSON)
            .body(Map.of("line", "ver"))
            .post("/api/complete")
            .then()
            .statusCode(200)
            .body("start", org.hamcrest.Matchers.is(0))
            .body("candidates.value", hasItem("version"));
    }

    @Test
    void completesPlatforms() {
        given().contentType(ContentType.JSON)
            .body(Map.of("line", "server add fa"))
            .post("/api/complete")
            .then()
            .statusCode(200)
            .body("start", org.hamcrest.Matchers.is(11))
            .body("candidates.value", org.hamcrest.Matchers.is(List.of("fabric")));
    }

    @Test
    void completesAtCursor() {
        given().contentType(ContentType.JSON)
            .body(Map.of("line", "acc list", "cursor", 3))
            .post("/api/complete")
            .then()
            .statusCode(200)
            .body("end", org.hamcrest.Matchers.is(3))
            .body("candidates.value", hasItem("account"));
    }

}
