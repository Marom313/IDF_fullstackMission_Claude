package com.ipp.policy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Hexagonal rules: domain and application never depend on Spring, Mongo or the web layer.
 * Plain {@code @Test} + {@code rule.check()} (not {@code @ArchTest}): under surefire the ArchUnit JUnit5 engine
 * reported 0 tests on main, so these could silently never run.
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ipp.policy");

    @Test
    void domainIsFrameworkFree() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "com.mongodb..", "com.fasterxml..")
                .check(CLASSES);
    }

    @Test
    void applicationIsFrameworkFree() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "com.mongodb..")
                .check(CLASSES);
    }

    @Test
    void innerLayersDoNotSeeAdapters() {
        noClasses().that().resideInAnyPackage("..domain..", "..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapters..")
                .check(CLASSES);
    }
}
