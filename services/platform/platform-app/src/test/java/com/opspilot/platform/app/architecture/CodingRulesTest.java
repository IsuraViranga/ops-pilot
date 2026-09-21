package com.opspilot.platform.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JODATIME;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Practices that are easy to slip into and awkward to remove once they have spread.
 *
 * <p>Checkstyle catches some of these within a single file. ArchUnit sees the whole application,
 * so it also catches the cases that only become visible across class boundaries.
 */
class CodingRulesTest {

    private static final String ROOT = "com.opspilot.platform";

    private static final JavaClasses PLATFORM = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    @DisplayName("nothing writes to System.out or System.err")
    void noStandardStreams() {
        // Output that bypasses the logger has no timestamp, no level and no correlation id,
        // and is invisible to log aggregation in production.
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.allowEmptyShould(true).check(PLATFORM);
    }

    @Test
    @DisplayName("nothing uses java.util.logging")
    void noJavaUtilLogging() {
        // One logging facade, so one configuration controls levels and formatting.
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.allowEmptyShould(true).check(PLATFORM);
    }

    @Test
    @DisplayName("nothing uses Joda-Time")
    void noJodaTime() {
        // java.time has been the answer since Java 8.
        NO_CLASSES_SHOULD_USE_JODATIME.allowEmptyShould(true).check(PLATFORM);
    }

    @Test
    @DisplayName("dependencies are injected through constructors, not fields")
    void noFieldInjection() {
        // Field injection hides dependencies from the constructor, so a class can grow eight
        // collaborators without anyone noticing, and it cannot be instantiated in a plain
        // unit test without reflection.
        fields().should()
                .notBeAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                .because("constructor injection makes dependencies visible and classes testable")
                .allowEmptyShould(true)
                .check(PLATFORM);
    }

    @Test
    @DisplayName("nothing throws raw RuntimeException or Exception")
    void noGenericExceptions() {
        // A generic exception cannot be handled selectively by a caller, and it reaches the
        // global handler with nothing useful to turn into a Problem Details response.
        noClasses()
                .should()
                .callConstructor(RuntimeException.class)
                .orShould()
                .callConstructor(RuntimeException.class, String.class)
                .orShould()
                .callConstructor(Exception.class)
                .orShould()
                .callConstructor(Exception.class, String.class)
                .because("throw a specific exception the caller and the error handler can act on")
                .allowEmptyShould(true)
                .check(PLATFORM);
    }
}
