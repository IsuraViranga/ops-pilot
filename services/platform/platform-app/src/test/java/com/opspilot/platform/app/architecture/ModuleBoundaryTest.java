package com.opspilot.platform.app.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * Executable form of the module boundaries described in ADR-0001.
 *
 * <p>These are the rules that make the Phase 4 microservice extraction a mechanical change
 * rather than a rewrite. Enforcing them in review does not work - reviewers get tired, and a
 * single {@code @ManyToOne} across a boundary is easy to miss and expensive to undo.
 *
 * <p>Failures here are not style opinions. Each one identifies a coupling that would have to
 * be unpicked before the offending module could become a service.
 */
class ModuleBoundaryTest {

    private static final String ROOT = "com.opspilot.platform";

    /** The composition root. It is allowed to see everything; that is its purpose. */
    private static final String APP = ROOT + ".app";

    /** The shared kernel. Every module may depend on it; it may depend on none of them. */
    private static final String COMMON = ROOT + ".common";

    /** Bounded contexts, each a candidate service in Phase 4. */
    private static final List<String> DOMAIN_MODULES =
            List.of("identity", "servicedesk", "sla", "workflow", "asset", "knowledge", "audit");

    /**
     * Production classes only. Tests legitimately reach across boundaries to build fixtures, so
     * including them would produce false failures.
     */
    private static final JavaClasses PLATFORM = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    @DisplayName("modules form no dependency cycles")
    void modulesAreFreeOfCycles() {
        // A cycle between two modules means neither can be extracted without the other,
        // which means they are one module wearing two names.
        SlicesRuleDefinition.slices()
                .matching(ROOT + ".(*)..")
                .should()
                .beFreeOfCycles()
                .allowEmptyShould(true)
                .check(PLATFORM);
    }

    @Test
    @DisplayName("the shared kernel depends on no other module")
    void commonDependsOnNoOtherModule() {
        // Everything depends on common. If common depended back on anything, every module
        // would transitively depend on every other, and nothing could ever be extracted.
        String[] everyOtherModule = DOMAIN_MODULES.stream()
                .map(module -> ROOT + "." + module + "..")
                .toArray(String[]::new);

        noClasses()
                .that()
                .resideInAPackage(COMMON + "..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(everyOtherModule)
                .because("the shared kernel must stay free of domain knowledge (ADR-0001)")
                .allowEmptyShould(true)
                .check(PLATFORM);
    }

    @Test
    @DisplayName("a module is reachable from other modules only through its api package")
    void crossModuleAccessGoesThroughApiOnly() {
        // The rule that does the real work. Reaching into another module's domain, repository
        // or web layer is exactly the coupling that makes a schema impossible to split.
        for (String module : DOMAIN_MODULES) {
            String modulePackage = ROOT + "." + module;

            DescribedPredicate<JavaClass> insideTheModuleButNotItsApi = resideInAPackage(modulePackage + "..")
                    .and(not(resideInAPackage(modulePackage + ".api..")))
                    .as("internals of " + module);

            noClasses()
                    .that()
                    .resideOutsideOfPackages(modulePackage + "..", APP + "..")
                    .should()
                    .dependOnClassesThat(insideTheModuleButNotItsApi)
                    .because(module + " publishes its api package; everything else is internal (ADR-0001)")
                    .allowEmptyShould(true)
                    .check(PLATFORM);
        }
    }

    @Test
    @DisplayName("the domain layer knows nothing about frameworks")
    void domainLayerIsFrameworkFree() {
        // Domain classes carrying JPA or Spring annotations cannot be unit tested without a
        // container, and they quietly make persistence decisions part of the business model.
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage(ROOT + ".*.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "jakarta.servlet..", "org.hibernate..")
                .because("domain logic must be testable without a Spring context (ADR-0001)")
                .allowEmptyShould(true);

        rule.check(PLATFORM);
    }

    @Test
    @DisplayName("no module depends on the composition root")
    void nothingDependsOnTheApplicationModule() {
        // Dependencies point inward. A module reaching back into app would make the app
        // impossible to replace - and in Phase 4 it is replaced by seven separate ones.
        noClasses()
                .that()
                .resideOutsideOfPackage(APP + "..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(APP + "..")
                .because("the composition root wires modules; modules must not know it exists")
                .allowEmptyShould(true)
                .check(PLATFORM);
    }
}
