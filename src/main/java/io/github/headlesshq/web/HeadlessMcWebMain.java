package io.github.headlesshq.web;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Explicit main, otherwise quarkus-picocli (needed to make HeadlessMc's commands CDI beans)
 * would run the HeadlessMc command line as application and exit, instead of serving the web ui.
 */
@QuarkusMain
public class HeadlessMcWebMain {
    public static void main(String... args) {
        Quarkus.run(args);
    }

}
