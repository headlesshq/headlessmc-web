package io.github.headlesshq.web;

import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

import java.time.Duration;
import java.util.Map;
import java.util.function.Predicate;

import static io.restassured.RestAssured.given;

final class JobTestSupport {
    private JobTestSupport() {
        throw new AssertionError();
    }

    static String submit(String line) {
        return given().contentType(ContentType.JSON)
            .body(Map.of("line", line, "origin", "test"))
            .post("/api/jobs")
            .then().statusCode(201)
            .extract().path("id");
    }

    static JsonPath job(String id) {
        return given().get("/api/jobs/" + id).then().statusCode(200).extract().jsonPath();
    }

    static JsonPath await(String id, Predicate<JsonPath> condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            JsonPath job = job(id);
            if (condition.test(job)) {
                return job;
            }

            Thread.sleep(50);
        }

        throw new AssertionError("Condition not met in time, job: " + job(id).prettify());
    }

    static JsonPath awaitDone(String id) throws InterruptedException {
        return await(id, job -> {
            String status = job.getString("status");
            return "SUCCEEDED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status);
        });
    }

    static void answer(String jobId, String promptId, String value) {
        given().contentType(ContentType.JSON)
            .body(Map.of("promptId", promptId, "value", value))
            .post("/api/jobs/" + jobId + "/input")
            .then().statusCode(200);
    }

}
