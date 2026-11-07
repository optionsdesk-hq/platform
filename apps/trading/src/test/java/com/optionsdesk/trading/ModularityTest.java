package com.optionsdesk.trading;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import org.springframework.web.bind.annotation.RestController;

/**
 * The architecture, enforced: Spring Modulith checks that modules only use each other's public API
 * and have no cycles; ArchUnit adds project rules. A violation fails the build, so the design can't
 * silently erode.
 */
class ModularityTest {

  static final ApplicationModules MODULES = ApplicationModules.of(TradingApplication.class);
  static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.optionsdesk.trading");

  @Test
  void modulesRespectTheirBoundaries() {
    MODULES.verify();
  }

  @Test
  void controllersLiveOnlyInTheApiModule() {
    classes()
        .that()
        .areAnnotatedWith(RestController.class)
        .should()
        .resideInAPackage("com.optionsdesk.trading.api..")
        .check(CLASSES);
  }

  /** The layers inside each module (see docs/architecture.md). */
  @Test
  void restControllersLiveInTheControllerLayer() {
    classes()
        .that()
        .areAnnotatedWith(RestController.class)
        .should()
        .resideInAPackage("com.optionsdesk.trading.api.controller..")
        .check(CLASSES);
  }

  @Test
  void repositoriesLiveInARepositoryLayer() {
    classes()
        .that()
        .areAnnotatedWith(org.springframework.stereotype.Repository.class)
        .should()
        .resideInAPackage("..repository..")
        .check(CLASSES);
  }

  @Test
  void springConfigurationLivesInAConfigLayer() {
    classes()
        .that()
        .areAnnotatedWith(org.springframework.context.annotation.Configuration.class)
        .and()
        .doNotHaveSimpleName("TradingApplication")
        .should()
        .resideInAPackage("..config..")
        .allowEmptyShould(true)
        .check(CLASSES);
  }

  @Test
  void controllersGoThroughServicesNotRepositories() {
    noClasses()
        .that()
        .resideInAPackage("..controller..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..repository..")
        .check(CLASSES);
  }

  @Test
  void onlyTheKiteAdapterMayUseTheBrokerSdk() {
    noClasses()
        .that()
        .resideOutsideOfPackage("com.optionsdesk.trading.kite..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.zerodhatech..")
        .allowEmptyShould(true)
        .check(CLASSES);
  }

  /**
   * Writes module diagrams (PlantUML) to build/spring-modulith-docs: living architecture
   * documentation.
   */
  @Test
  void documentModules() {
    new Documenter(MODULES).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
  }
}
