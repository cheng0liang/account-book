package com.accountbook.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Executable form of the module rules in .specify/memory/constitution.md. */
@AnalyzeClasses(packages = "com.accountbook", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    private static ArchRule internalIsPrivate(String module) {
        return noClasses().that().resideOutsideOfPackages("com.accountbook." + module + "..")
                .should().dependOnClassesThat().resideInAPackage("com.accountbook." + module + ".internal..")
                .as("only " + module + " itself may touch " + module + ".internal");
    }

    @ArchTest
    static final ArchRule merchantInternal = internalIsPrivate("merchant");
    @ArchTest
    static final ArchRule productInternal = internalIsPrivate("product");
    @ArchTest
    static final ArchRule ocrInternal = internalIsPrivate("ocr");
    @ArchTest
    static final ArchRule receiptInternal = internalIsPrivate("receipt");
    @ArchTest
    static final ArchRule analyticsInternal = internalIsPrivate("analytics");
    @ArchTest
    static final ArchRule userInternal = internalIsPrivate("user");
    @ArchTest
    static final ArchRule familyInternal = internalIsPrivate("family");

    @ArchTest
    static final ArchRule commonDependsOnNoModule = noClasses().that().resideInAPackage("com.accountbook.common..")
            .should().dependOnClassesThat().resideInAnyPackage("com.accountbook.merchant..",
                    "com.accountbook.product..", "com.accountbook.ocr..", "com.accountbook.receipt..",
                    "com.accountbook.analytics..", "com.accountbook.user..", "com.accountbook.family..");

    @ArchTest
    static final ArchRule leafModulesAreIndependent = noClasses()
            .that().resideInAnyPackage("com.accountbook.merchant..", "com.accountbook.product..", "com.accountbook.ocr..")
            .should().dependOnClassesThat().resideInAnyPackage("com.accountbook.receipt..", "com.accountbook.analytics..",
                    "com.accountbook.user..", "com.accountbook.family..")
            .as("merchant/product/ocr are global catalogues: no dependency on receipt, analytics, user or family");

    @ArchTest
    static final ArchRule merchantProductOcrDoNotKnowEachOther = noClasses()
            .that().resideInAPackage("com.accountbook.merchant..")
            .should().dependOnClassesThat().resideInAnyPackage("com.accountbook.product..", "com.accountbook.ocr..");

    @ArchTest
    static final ArchRule productDoesNotKnowMerchant = noClasses()
            .that().resideInAPackage("com.accountbook.product..")
            .should().dependOnClassesThat().resideInAnyPackage("com.accountbook.merchant..", "com.accountbook.ocr..");

    @ArchTest
    static final ArchRule ocrDoesNotKnowOthers = noClasses()
            .that().resideInAPackage("com.accountbook.ocr..")
            .should().dependOnClassesThat().resideInAnyPackage("com.accountbook.merchant..", "com.accountbook.product..");

    @ArchTest
    static final ArchRule userDependsOnlyOnCommon = noClasses()
            .that().resideInAPackage("com.accountbook.user..")
            .should().dependOnClassesThat().resideInAnyPackage("com.accountbook.merchant..", "com.accountbook.product..",
                    "com.accountbook.ocr..", "com.accountbook.family..", "com.accountbook.receipt..",
                    "com.accountbook.analytics..");

    @ArchTest
    static final ArchRule familyOnlyKnowsUser = noClasses()
            .that().resideInAPackage("com.accountbook.family..")
            .should().dependOnClassesThat().resideInAnyPackage("com.accountbook.merchant..", "com.accountbook.product..",
                    "com.accountbook.ocr..", "com.accountbook.receipt..", "com.accountbook.analytics..");

    @ArchTest
    static final ArchRule receiptDoesNotKnowAnalytics = noClasses()
            .that().resideInAPackage("com.accountbook.receipt..")
            .should().dependOnClassesThat().resideInAPackage("com.accountbook.analytics..");

    @ArchTest
    static final ArchRule apiPackagesDoNotUseInternal = classes()
            .that().resideInAPackage("..api..")
            .should().onlyDependOnClassesThat().resideOutsideOfPackage("..internal..");
}
