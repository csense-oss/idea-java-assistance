package csense.idea.java.assistance.visitors

import com.intellij.psi.*
import csense.kotlin.Function0


class PsiClassVisitor(
        val block: Function0<PsiClass>
) : PsiElementVisitor() {
    override fun visitElement(element: PsiElement?) {
        if (element is PsiClass) {
            block(element)
        }
    }
}