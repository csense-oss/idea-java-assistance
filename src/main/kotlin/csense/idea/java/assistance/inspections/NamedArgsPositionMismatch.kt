package csense.idea.java.assistance.inspections

import com.intellij.codeHighlighting.*
import com.intellij.codeInspection.*
import com.intellij.psi.*
import com.intellij.psi.impl.source.*
import csense.idea.java.assistance.Constants
import csense.idea.java.assistance.suppression.*
import csense.idea.java.assistance.visitors.*

class NamedArgsPositionMismatch : LocalInspectionTool(), CustomSuppressableInspectionTool {

    override fun getDisplayName(): String {
        return "Mismatched naming for parameter names"
    }

    override fun getStaticDescription(): String? {
        //the ctrl  + f1 box +  desc of the inspection.
        return """
            This inspection tells whenever a used name (such as a variable)
                is passed to / or from a function where that name is also used but at a different location.
            This generally is an error, such as swapping arguments around or parameter names for that matter.
        """.trimIndent()
    }

    override fun getDescriptionFileName(): String? {
        return "more desc ? "
    }

    override fun getShortName(): String {
        return "NamedArgsPositionMismatch"
    }

    override fun getGroupDisplayName(): String {
        return Constants.InspectionGroupName
    }

    override fun isEnabledByDefault(): Boolean {
        return true
    }

    override fun getDefaultLevel(): HighlightDisplayLevel {
        return HighlightDisplayLevel.ERROR
    }


    override fun getSuppressActions(element: PsiElement?): Array<SuppressIntentionAction>? {
        return arrayOf(
                PsiExpressionSuppression("Suppress naming mismatch issue", groupDisplayName, shortName))
    }

    override fun buildVisitor(holder: ProblemsHolder,
                              isOnTheFly: Boolean): PsiElementVisitor {
        return PsiCallExpressionVisitor { call: PsiCallExpression ->

            if (call.argumentList?.isEmpty != false) {
                //no arguments to check
                return@PsiCallExpressionVisitor
            }

            val callingFunction = call.resolveMethod() ?: return@PsiCallExpressionVisitor
            val usedNames = call.findInvocationArgumentNames()
            val originalParameterNames = callingFunction.findOriginalMethodArgumentNames()
            if (usedNames.size > originalParameterNames.size) {
                //invalid code, just skip. (invoking with more args than there is).
                return@PsiCallExpressionVisitor
            }
            val misMatches = computeMismatchingNames(usedNames, originalParameterNames)
            if (misMatches.isNotEmpty()) {
                reportProblem(call, misMatches, holder)
            }

            call.argumentList?.expressions?.mapNotNull {
                it as? PsiLambdaExpression
            }?.forEach {
                val orgClass = it.functionalInterfaceType as? PsiClassReferenceType ?: return@forEach
                val realClass = orgClass.resolve() ?: return@forEach
                //since we have a functional interface type, we KNOW we only have a single function in an interface
                //thus
                val functionalMethod = realClass.methods.firstOrNull() ?: return@forEach
                val usedLambdaParameterNames = functionalMethod.parameterList.parameters.map { lambdaParam -> lambdaParam.name }
                val callingLambdaParameterNames = it.parameterList.parameters.map { lambdaParam -> lambdaParam.name }
                val mismatchedLambdaNames = computeMismatchingNames(
                        callingLambdaParameterNames,
                        usedLambdaParameterNames)
                if (mismatchedLambdaNames.isNotEmpty()) {
                    reportLambdaProblem(it, mismatchedLambdaNames, holder)
                }
            }
        }
    }

    fun reportProblem(atElement: PsiElement, mismatches: List<MismatchedName>, holder: ProblemsHolder) {
        val names = mismatches.joinToString(",") {
            it.name
        }
        holder.registerProblem(atElement,
                "You have mismatched arguments names \n($names)")
    }

    fun reportLambdaProblem(atElement: PsiElement, mismatches: List<MismatchedName>, holder: ProblemsHolder) {
        val names = mismatches.joinToString(",") {
            "\"${it.name}\" - should be at position ${it.shouldBeAtIndex}"
        }
        holder.registerProblem(atElement,
                "You have mismatched arguments names \n($names)")
    }


    fun computeMismatchingNames(usedNames: List<String?>, originalParameterNames: List<String?>): List<MismatchedName> {
        val originalNames = originalParameterNames.filterNotNull().toSet()
        val result = mutableListOf<MismatchedName>()
        usedNames.forEachIndexed { index, name ->
            if (name == null || !originalNames.contains(name)) {
                return@forEachIndexed
            }
            //only look at those who are contained.
            val org = originalParameterNames[index]
            if (org == null || org != name) {
                //ERROR !! mismatching name but is declared somewhere else.
                result.add(MismatchedName(name, index, originalParameterNames.indexOf(name)))
            }
        }
        return result
    }


}


data class MismatchedName(val name: String, val parameterIndex: Int, val shouldBeAtIndex: Int)


fun PsiCallExpression.findInvocationArgumentNames(): List<String?> {
    return argumentList?.expressions?.mapNotNull {
        if (it is PsiReferenceExpression) {
            it.referenceName
        } else {
            null
        }
    } ?: listOf()
}

/**
 *
 * @return List<String> the order of the arguments as well as the name
 */
fun PsiMethod.findOriginalMethodArgumentNames(): List<String> {
    return parameters.map { param ->
        param.name ?: ""
    }
}

