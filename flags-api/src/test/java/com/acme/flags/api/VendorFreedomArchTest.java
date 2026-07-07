package com.acme.flags.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class VendorFreedomArchTest {

    @Test
    void apiAndSpiPackagesOnlyDependOnAllowedPackages() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.acme.flags");

        ArchRule rule = classes()
                .that().resideInAnyPackage("com.acme.flags.api..", "com.acme.flags.spi..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.acme.flags.api..",
                        "com.acme.flags.spi..",
                        "io.micrometer..",
                        "org.slf4j..",
                        "java..");

        rule.check(classes);
    }
}
