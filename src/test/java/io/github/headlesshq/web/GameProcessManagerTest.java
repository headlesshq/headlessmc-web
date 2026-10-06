package io.github.headlesshq.web;

import io.github.headlesshq.headlessmc.java.launcher.JavaProcessBuilder;
import io.github.headlesshq.headlessmc.launcher.process.McProcess;
import io.github.headlesshq.headlessmc.launcher.process.ProcessLauncher;
import io.github.headlesshq.web.process.GameProcessManager;
import io.github.headlesshq.web.process.ManagedProcess;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class GameProcessManagerTest {
    @Inject
    GameProcessManager manager;

    @Test
    void pipesOutputAndInput() throws Exception {
        ManagedProcess process = manager.launch(new ShellLauncher("echo started; read line; echo got $line"), 0);
        awaitTrue(() -> process.getHistory().stream().anyMatch(line -> line.text().equals("started")));

        given().contentType("application/json")
            .body("{\"line\":\"hello\"}")
            .post("/api/processes/" + process.getId() + "/input")
            .then().statusCode(200);

        awaitTrue(() -> process.getStatus() == ManagedProcess.Status.EXITED);
        assertEquals(0, process.getExitCode());
        assertTrue(process.getHistory().stream().anyMatch(line -> line.text().equals("got hello")));

        given().delete("/api/processes/" + process.getId()).then().statusCode(204);
    }

    @Test
    void retriesFailedProcesses() throws Exception {
        ManagedProcess process = manager.launch(new ShellLauncher("exit 3"), 2);
        awaitTrue(() -> process.getStatus() == ManagedProcess.Status.EXITED);
        assertEquals(3, process.getAttempt());
        assertEquals(3, process.getExitCode());
    }

    @Test
    void stopsProcesses() throws Exception {
        ManagedProcess process = manager.launch(new ShellLauncher("sleep 60"), 5);
        given().post("/api/processes/" + process.getId() + "/stop").then().statusCode(200);
        awaitTrue(() -> process.getStatus() == ManagedProcess.Status.STOPPED);
        assertEquals(1, process.getAttempt());
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Condition not met in time");
            }

            Thread.sleep(25);
        }
    }

    private record ShellLauncher(String script) implements ProcessLauncher {
        @Override
        public McProcess launch(boolean pipeIO) {
            try {
                Process process = new ProcessBuilder("sh", "-c", script).start();
                return new McProcess("test", Optional.empty(), Optional.of(process));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Optional<JavaProcessBuilder> getJavaProcessBuilder() {
            return Optional.empty();
        }

        @Override
        public Optional<ProcessBuilder> getProcessBuilder() {
            return Optional.empty();
        }

        @Override
        public Path getGameDir() {
            return Path.of(".");
        }

        @Override
        public String getId() {
            return "test-" + script.hashCode();
        }
    }

}
