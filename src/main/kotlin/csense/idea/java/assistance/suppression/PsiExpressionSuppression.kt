package csense.idea.java.assistance.suppression

import com.intellij.codeInspection.*
import com.intellij.openapi.editor.*
import com.intellij.openapi.project.*
import com.intellij.psi.*
import com.intellij.util.*
import csense.idea.java.assistance.inspections.*


class PsiExpressionSuppression(
        val displayText: String,
        val familyNameToUse: String,
        val shortName: String
) : SuppressIntentionAction() {
    override fun getFamilyName(): String {
        return familyNameToUse
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return true
    }

    override fun getText(): String {
        return displayText
    }

    @Throws(IncorrectOperationException::class)
    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val exp: Pair<PsiCodeBlock, PsiElement> =
                element.findParentAndBeforeFromType() ?: return
        val factory = PsiElementFactory.SERVICE.getInstance(project)
        val annotation =
                factory.createCommentFromText("//noinspection $shortName", null)
        exp.first.addBefore(annotation, exp.second)
    }

}