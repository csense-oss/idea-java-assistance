package csense.idea.java.assistance.visitors

import com.intellij.psi.*
import csense.kotlin.Function0


class PsiCallExpressionVisitor(
        val block: Function0<PsiCallExpression>
) : PsiElementVisitor() {
    override fun visitElement(element: PsiElement?) {
        if (element is PsiCallExpression) {
            block(element)
        }
    }
}