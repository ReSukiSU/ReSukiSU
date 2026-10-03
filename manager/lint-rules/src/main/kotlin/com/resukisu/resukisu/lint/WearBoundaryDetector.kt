package com.resukisu.resukisu.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UImportStatement

/** Keeps the phone and Wear UI components apart: neither side imports the other's components. */
class WearBoundaryDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes() = listOf(UImportStatement::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitImportStatement(node: UImportStatement) {
            val imported = node.importReference?.asSourceString() ?: return
            val filePackage = context.uastFile?.packageName ?: return
            val inWear = filePackage == WEAR_PACKAGE || filePackage.startsWith("$WEAR_PACKAGE.")
            val message = when {
                inWear && imported.startsWith("$PHONE_COMPONENT_PACKAGE.") && imported !in SHARED_COMPONENTS ->
                    "Wear UI should not use phone components from $PHONE_COMPONENT_PACKAGE; use $WEAR_COMPONENT_PACKAGE."
                !inWear && imported.startsWith("$WEAR_COMPONENT_PACKAGE.") ->
                    "Phone UI should not use Wear components from $WEAR_COMPONENT_PACKAGE."
                else -> return
            }
            context.report(ISSUE, node, context.getLocation(node), message)
        }
    }

    companion object {
        private const val PHONE_COMPONENT_PACKAGE = "com.resukisu.resukisu.ui.component"
        private const val WEAR_PACKAGE = "com.resukisu.resukisu.ui.wear"
        private const val WEAR_COMPONENT_PACKAGE = "$WEAR_PACKAGE.component"

        /**
         * Phone components without a phone-only layout that Wear shares: the dialog API, which
         * AGENTS.md requires for every custom dialog, and the app icon loader.
         */
        private val SHARED_COMPONENTS = setOf(
            "$PHONE_COMPONENT_PACKAGE.rememberCustomDialog",
            "$PHONE_COMPONENT_PACKAGE.DialogHandle",
            "$PHONE_COMPONENT_PACKAGE.PackageIcon",
        )

        val ISSUE: Issue = Issue.create(
            id = "WearPhoneComponentBoundary",
            briefDescription = "Phone and Wear UI components are mixed",
            explanation = "Phone screens use components from ui.component and Wear screens use components " +
                "from ui.wear.component. Importing across the two keeps watch-specific layouts out of the " +
                "phone UI and the other way around.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                WearBoundaryDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
