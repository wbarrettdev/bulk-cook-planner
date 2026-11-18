package io.github.wbarrettdev.bulkcook.knowledge;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Keeps the layers honest: the build fails if the domain starts to lean on anything outside it. */
class ArchitectureTest {

    private static final JavaClasses SERVICE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackagesOf(KnowledgeApplication.class);

    /** Lombok is allowed because it only writes code at compile time and leaves a marker behind. */
    @Test
    void theDomainUsesOnlyItselfAndJava() {
        classes()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("..domain..", "java..", "lombok..")
                .check(SERVICE);
    }

    @Test
    void eachLayerLooksOnlyInwards() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("domain")
                .definedBy("..domain..")
                .layer("application")
                .definedBy("..application..")
                .layer("infrastructure")
                .definedBy("..infrastructure..")
                .whereLayer("infrastructure")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("application")
                .mayOnlyBeAccessedByLayers("infrastructure")
                .whereLayer("domain")
                .mayOnlyBeAccessedByLayers("application", "infrastructure")
                .check(SERVICE);
    }
}
