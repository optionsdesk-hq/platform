package com.optionsdesk.shared;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The shared kernel is pure: no framework, no broker SDK, no I/O libraries. Every app depends on
 * it.
 */
@AnalyzeClasses(
    packages = "com.optionsdesk.shared",
    importOptions = ImportOption.DoNotIncludeTests.class)
class SharedKernelArchitectureTest {

  @ArchTest
  static final ArchRule noFrameworks =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..", "com.zerodhatech..", "java.sql..", "java.net..")
          .because("the shared kernel must stay pure and usable everywhere");

  @ArchTest
  static final ArchRule domainDependsOnNothingElse =
      noClasses()
          .that()
          .resideInAPackage("..shared.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..shared.pricing..", "..shared.calendar..")
          .because("domain types are the base layer");
}
