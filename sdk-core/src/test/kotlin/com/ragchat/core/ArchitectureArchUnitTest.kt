package com.ragchat.core

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.Test

public class ArchitectureArchUnitTest {
    @Test
    public fun pure_modules_must_not_depend_on_android_packages() {
        val importedClasses = ClassFileImporter().importPackages("com.ragchat.core..")
        val rule = noClasses().should().dependOnClassesThat().resideInAPackage("android..")
        rule.check(importedClasses)
    }
}
