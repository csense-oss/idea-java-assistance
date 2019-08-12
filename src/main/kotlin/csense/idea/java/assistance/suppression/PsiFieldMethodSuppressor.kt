package csense.idea.java.assistance.suppression

import com.intellij.codeInspection.*
import com.intellij.openapi.editor.*
import com.intellij.openapi.project.*
import com.intellij.psi.*
import com.intellij.util.*
import csense.idea.java.assistance.inspections.*


class PsiFieldMethodSuppressor(
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
        val field = element.findContainingField()
        val function = element.findContainingMethod()
        if (field == null && function == null) {
            return
        }

        val factory = PsiElementFactory.SERVICE.getInstance(project)
        val annotation = factory.createAnnotationFromText("@SuppressWarnings(\"$shortName\")", null)
        when {
            field != null -> field.parent?.addBefore(annotation, field)
            function != null -> function.parent?.addBefore(annotation, function)
        }
    }
}