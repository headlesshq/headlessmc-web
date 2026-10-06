package io.github.headlesshq.web.console;

import jakarta.inject.Qualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Qualifies the beans the web layer plugs into HeadlessMc's SPI ({@link WebConsoleProvider}),
 * so they do not clash with HeadlessMc's own {@code @Default} beans.
 */
@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface Web {
}
