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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.getParentOfType

class SegmentedColumnScopeConditionDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes() = listOf(
        UIfExpression::class.java,
        USwitchExpression::class.java,
    )

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitIfExpression(node: UIfExpression) {
            checkCondition(context, node, "if")
        }

        override fun visitSwitchExpression(node: USwitchExpression) {
            checkCondition(context, node, "when")
        }
    }

    private fun checkCondition(context: JavaContext, node: UExpression, keyword: String) {
        if (!isInSegmentedColumnScope(node)) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Do not use `$keyword` directly in `SegmentedColumnScope`; use `item(visible = ...)` or move the condition outside the DSL."
        )
    }

    private fun isInSegmentedColumnScope(node: UElement): Boolean {
        var current = node.uastParent
        while (current != null) {
            when (current) {
                is ULambdaExpression -> return current.isSegmentedColumnScopeLambda()
                is UMethod -> return current.isSegmentedColumnScopeExtension()
            }
            current = current.uastParent
        }
        return false
    }

    private fun ULambdaExpression.isSegmentedColumnScopeLambda(): Boolean {
        val call = getParentOfType<UCallExpression>(UCallExpression::class.java, true)
            ?: return false
        val method = call.resolve() ?: return false

        if (!method.hasSegmentedColumnScopeParameter()) return false

        val argumentIndex = call.valueArguments.indexOfFirst { argument ->
            argument == this || argument.sourcePsi == sourcePsi
        }
        if (argumentIndex == -1) return false

        val parameter = method.parameterList.parameters.getOrNull(argumentIndex) ?: return false
        return parameter.type.canonicalText.contains(SEGMENTED_COLUMN_SCOPE)
    }

    private fun UMethod.isSegmentedColumnScopeExtension(): Boolean {
        return javaPsi?.parameterList?.parameters
            ?.firstOrNull()
            ?.type
            ?.canonicalText
            ?.contains(SEGMENTED_COLUMN_SCOPE) == true
    }

    private fun PsiMethod.hasSegmentedColumnScopeParameter(): Boolean {
        return parameterList.parameters.any { parameter ->
            parameter.type.canonicalText.contains(SEGMENTED_COLUMN_SCOPE)
        }
    }

    companion object {
        private const val SEGMENTED_COLUMN_SCOPE = "SegmentedColumnScope"

        val ISSUE: Issue = Issue.create(
            id = "SegmentedColumnScopeCondition",
            briefDescription = "Conditional control flow in SegmentedColumnScope",
            explanation = "SegmentedColumnScope should keep its item structure explicit. Use the DSL's visible parameter or compute data before entering the scope instead of using if/when directly in the scope.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                SegmentedColumnScopeConditionDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
