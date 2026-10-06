package io.github.headlesshq.web;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import static io.github.headlesshq.web.JobTestSupport.answer;
import static io.github.headlesshq.web.JobTestSupport.await;
import static io.github.headlesshq.web.JobTestSupport.awaitDone;
import static io.github.headlesshq.web.JobTestSupport.submit;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class JobResourceTest {
    @Test
    void executesCommands() throws InterruptedException {
        JsonPath job = awaitDone(submit("config get hmc.java.download"));
        assertEquals("SUCCEEDED", job.getString("status"), job::prettify);
        assertTrue(job.getString("output").contains("hmc.java.download"), job::prettify);
    }

    @Test
    void showsUsageHelp() throws InterruptedException {
        JsonPath job = awaitDone(submit("help version"));
        assertEquals("SUCCEEDED", job.getString("status"), job::prettify);
        assertTrue(job.getString("output").contains("install"), job::prettify);
    }

    @Test
    void reportsParameterErrors() throws InterruptedException {
        JsonPath job = awaitDone(submit("this-command-does-not-exist"));
        assertEquals("FAILED", job.getString("status"), job::prettify);
        // queued (0) -> running (1) -> failed (2), clients use the version to drop outdated snapshots
        assertEquals(2, job.getLong("version"), job::prettify);
        assertTrue(job.getString("output").contains("this-command-does-not-exist"), job::prettify);
    }

    @Test
    void reportsExecutionErrors() throws InterruptedException {
        JsonPath job = awaitDone(submit("profile launch does-not-exist"));
        assertEquals("FAILED", job.getString("status"), job::prettify);
        assertTrue(job.getString("output").contains("Failed to find profile with name does-not-exist"), job::prettify);
    }

    @Test
    void doesNotSupportExit() throws InterruptedException {
        JsonPath job = awaitDone(submit("exit"));
        assertEquals("FAILED", job.getString("status"), job::prettify);
    }

    @Test
    void forwardsPromptsToTheBrowser() throws InterruptedException {
        String id = submit("account -p offline login");
        JsonPath job = await(id, j -> j.get("prompt") != null);
        assertEquals("Enter the account name:", job.getString("prompt.message"));
        answer(id, job.getString("prompt.id"), "WebTester");

        String previous = job.getString("prompt.id");
        job = await(id, j -> j.get("prompt") != null && !previous.equals(j.getString("prompt.id")));
        assertTrue(job.getString("prompt.message").contains("uuid"), job::prettify);
        answer(id, job.getString("prompt.id"), "");

        String uuidPrompt = job.getString("prompt.id");
        job = await(id, j -> j.get("prompt") != null && !uuidPrompt.equals(j.getString("prompt.id")));
        assertEquals("password", job.getString("prompt.kind"), job::prettify);
        answer(id, job.getString("prompt.id"), "secret-token");

        String tokenPrompt = job.getString("prompt.id");
        job = await(id, j -> j.get("prompt") != null && !tokenPrompt.equals(j.getString("prompt.id")));
        answer(id, job.getString("prompt.id"), "");

        job = awaitDone(id);
        assertEquals("SUCCEEDED", job.getString("status"), job::prettify);
        assertTrue(job.getString("output").contains("WebTester"), job::prettify);
        assertTrue(!job.getString("output").contains("secret-token"), job::prettify);

        given().get("/api/accounts")
            .then()
            .statusCode(200)
            .body("providers.find { it.name == 'offline' }.accounts.name", hasItem("WebTester"))
            .body("selected.name", org.hamcrest.Matchers.is("WebTester"));
    }

    @Test
    void cancelsPrompts() throws InterruptedException {
        String id = submit("account -p offline login");
        await(id, j -> j.get("prompt") != null);
        given().post("/api/jobs/" + id + "/cancel").then().statusCode(200);
        JsonPath job = awaitDone(id);
        assertEquals("CANCELLED", job.getString("status"), job::prettify);
    }

}
