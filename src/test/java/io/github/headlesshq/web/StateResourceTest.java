package io.github.headlesshq.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class StateResourceTest {
    @Test
    void info() {
        given().get("/api/info").then().statusCode(200)
            .body("name", org.hamcrest.Matchers.is("HeadlessMc"))
            .body("directories.data", notNullValue());
    }

    @Test
    void config() {
        given().get("/api/config").then().statusCode(200)
            .body("name", hasItem("hmc.java.download"));
    }

    @Test
    void lists() {
        given().get("/api/accounts").then().statusCode(200).body("providers.name", hasItem("offline"));
        given().get("/api/profiles").then().statusCode(200);
        given().get("/api/servers").then().statusCode(200);
        given().get("/api/versions").then().statusCode(200);
        given().get("/api/java").then().statusCode(200);
        given().get("/api/processes").then().statusCode(200);
    }

}
