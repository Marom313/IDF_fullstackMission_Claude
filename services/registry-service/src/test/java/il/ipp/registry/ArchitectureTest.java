package il.ipp.registry;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Hexagonal rules: the domain knows no framework, SQL or adapters; application does not use adapters. */
class ArchitectureTest {
  private static final JavaClasses CLASSES = new ClassFileImporter()
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("il.ipp.registry");

  @Test
  void domainIsFrameworkFree() {
    noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "jakarta..", "org.hibernate..", "java.sql..", "..adapters..",
            "..application..")
        .check(CLASSES);
  }

  @Test
  void applicationDoesNotUseAdapters() {
    noClasses().that().resideInAPackage("..application..").should().dependOnClassesThat()
        .resideInAPackage("..adapters..").check(CLASSES);
  }
}
