package csense.idea.java.assistance.inspections

import com.intellij.codeHighlighting.*
import com.intellij.codeInspection.*
import com.intellij.psi.*
import csense.idea.java.assistance.*
import csense.idea.java.assistance.quickfixes.*
import csense.idea.java.assistance.suppression.*
import csense.idea.java.assistance.visitors.*


class InitializationOrder : LocalInspectionTool(), CustomSuppressableInspectionTool {

    override fun getDisplayName(): String {
        return "Initialization order"
    }

    override fun getStaticDescription(): String? {
        return """
            This inspection tells whenever you have an "invalid" initialization order.
            This is because the JVM does not guarantee all scenarios.
            This means that declaration order defines initialization order, and so on (for example what about inheritance ?)

        """.trimIndent()
    }

    override fun getDescriptionFileName(): String? {
        return "more desc ? "
    }

    override fun getShortName(): String {
        return "InitOrder"
    }

    override fun getGroupDisplayName(): String {
        return Constants.InspectionGroupName
    }

    override fun getDefaultLevel(): HighlightDisplayLevel {
        return HighlightDisplayLevel.ERROR
    }

    override fun isEnabledByDefault(): Boolean {
        return true
    }

    override fun getSuppressActions(element: PsiElement?): Array<SuppressIntentionAction>? {
        return arrayOf(
                PsiFieldMethodSuppressor("Suppress initialization issue", groupDisplayName, shortName))
    }

    override fun buildVisitor(holder: ProblemsHolder,
                              isOnTheFly: Boolean): PsiElementVisitor {
        return PsiClassVisitor { ourClass: PsiClass ->
            val fields = ourClass.fields
            val nonDelegatesQuickLookup = ourClass.computeQuickIndexedNameLookup()
            val ourFqName = ourClass.fqName() ?: return@PsiClassVisitor
            fields.forEach { prop: PsiField ->
                val propName = prop.name
                val localRefs = prop.findLocalReferencesForInitializer(
                        ourFqName,
                        nonDelegatesQuickLookup.keys)
                val invalidOrders = localRefs.resolveInvalidOrders(propName, nonDelegatesQuickLookup)
                if (invalidOrders.isNotEmpty()) {
                    holder.registerProblem(prop,
                            createErrorDescription(invalidOrders),
                            *createQuickFixes(ourClass)
                    )
                }
            }
        }
    }


    fun createQuickFixes(classObj: PsiClass): Array<LocalQuickFix> {
        return arrayOf(
                MoveDeclarationsQuickFix(classObj)
        )
    }

    fun createErrorDescription(invalidOrders: List<DangerousReference>): String {

        val haveInnerInvalid = invalidOrders.joinToString(",") {
            it.innerReferences.joinToString(",\"", "\"", "\"") { exp -> exp.referenceName ?: "" }
        }
        val innerMessage = if (haveInnerInvalid.isNotBlank()) {
            "\n(Indirect dangerous references = $haveInnerInvalid)\n"
        } else {
            ""
        }

        val invalidOrdersNames = invalidOrders.map {
            it.mainReference.referenceName
        }.toSet().joinToString("\",\"", prefix = "\"", postfix = "\"")
        return "Initialization order is invalid for $invalidOrdersNames\n" +
                innerMessage +
                "It can / will result in null at runtime(Due to the JVM)"
    }


}

private fun PsiClass.computeQuickIndexedNameLookup(): Map<String, Int> {
    val resultingMap = mutableMapOf<String, Int>()
    val allProps = collectDescendantsOfType<PsiField>()

    allProps.forEach { prop ->
        val name = prop.name
        resultingMap[name] = prop.startOffsetInParent
    }


    val allFuns = collectDescendantsOfType<PsiMethod>()
    allFuns.forEach { function ->
        val name = function.name
        resultingMap[name] = function.startOffsetInParent
    }
    return resultingMap
}

fun PsiElement.findLocalReferences(
        ourFqNameStart: String,
        nonDelegatesQuickLookup: Set<String>
): List<PsiReferenceExpression> {
    return collectDescendantsOfType { nameRef: PsiReferenceExpression ->
        val name = nameRef.referenceName ?: return@collectDescendantsOfType false
        val fqName = nameRef.fqName() ?: return@collectDescendantsOfType false
        return@collectDescendantsOfType fqName.startsWith(ourFqNameStart) &&
                nonDelegatesQuickLookup.contains(name)
    }
}

fun PsiField.findLocalReferencesForInitializer(
        ourFqNameStart: String,
        nonDelegatesQuickLookup: Set<String>
): List<DangerousReference> {
    return initializer?.collectDescendantsOfType { nameRef: PsiReferenceExpression ->
        //skip things that are "ok" / legit.
        nameRef.isPotentialDangerousReference(
                ourFqNameStart,
                nonDelegatesQuickLookup)
    }?.map {
        DangerousReference(it,
                resolveInnerDangerousReferences(
                        ourFqNameStart,
                        nonDelegatesQuickLookup,
                        it.resolve()))
    } ?: return listOf()
}


private fun PsiReferenceExpression.isPotentialDangerousReference(
        ourFqNameStart: String,
        nonDelegatesQuickLookup: Set<String>
): Boolean {
    val referre = this.resolve() ?: return false

    val name = referenceName ?: return false
    val fqName = fqName() ?: return false
    val isInOurClass = fqName.startsWith(ourFqNameStart) &&
            nonDelegatesQuickLookup.contains(name)
    return when (referre) {
        is PsiMethod -> {
            return resolveInnerDangerousReferences(
                    ourFqNameStart,
                    nonDelegatesQuickLookup,
                    referre).isNotEmpty()
        }
        is PsiField -> {
            return true //we are referencing another field, that is by definition dangerous.
        }
        else -> isInOurClass
    }
}


private fun resolveInnerDangerousReferences(
        ourFqNameStart: String,
        nonDelegatesQuickLookup: Set<String>,
        mainDescriptor: PsiElement?
): List<PsiReferenceExpression> {
    return when (mainDescriptor) {
        is PsiField, is PsiMethod -> {
            mainDescriptor.findLocalReferences(ourFqNameStart, nonDelegatesQuickLookup)
        }
        else -> listOf()
    }
}

private fun List<PsiReferenceExpression>.isAllNotBefore(
        ourIndex: Int,
        order: Map<String, Int>
) = !all { it.isBefore(ourIndex, order) }


private fun PsiReferenceExpression.isBefore(
        ourIndex: Int,
        order: Map<String, Int>): Boolean {
    val itName = referenceName
    val itOrder = (order[itName] ?: Int.MAX_VALUE)
    return itOrder < ourIndex || (resolve() is PsiMethod)
}


fun List<DangerousReference>.resolveInvalidOrders(
        name: String,
        order: Map<String, Int>
): List<DangerousReference> {
    val ourIndex = order[name] ?: return listOf() //should not return.... :/ ???
    return filter { ref ->
        val isMainRefOk = ref.mainReference.isBefore(ourIndex, order)

        //if we reference something that is declared after us, its an "issue".
        !isMainRefOk || ref.innerReferences.isAllNotBefore(ourIndex, order)
    }
}


class DangerousReference(
        val mainReference: PsiReferenceExpression,
        val innerReferences: List<PsiReferenceExpression>)

fun PsiElement.fqName(): String? {
    return if (this is PsiClass) {
        qualifiedName
    } else {//if reference, we are to go to the reference first...
        findParentClass()?.qualifiedName
    }
}


fun PsiElement.findParentClass(): PsiClass? = findParentOfType()

inline fun PsiElement.findContainingBlock(): PsiCodeBlock? = findParentOfType()

inline fun PsiElement.findContainingField(): PsiField? = findParentOfType()

inline fun PsiElement.findContainingMethod(): PsiMethod? = findParentOfType()

inline fun <reified T : Any> PsiElement.findParentOfType(): T? =
        findParentAndBeforeFromType<T>()?.first

inline fun <reified T : Any> PsiElement.findParentAndBeforeFromType(

): Pair<T, PsiElement>? {
    var currentElement: PsiElement? = this
    var previousType = this
    while (currentElement != null) {
        if (currentElement is T) {
            return Pair(currentElement, previousType)
        }
        previousType = currentElement
        currentElement = currentElement.parent
    }
    return null
}

//from https://github.com/JetBrains/kotlin/blob/ada41fb23f87c9a3d2c013354f19dcb3eb292fbb/compiler/psi/src/org/jetbrains/kotlin/psi/psiUtil/psiUtils.kt
//since the kotlin plugin is NOT available for pure java...
inline fun <reified T : PsiElement> PsiElement.collectDescendantsOfType(noinline predicate: (T) -> Boolean = { true }): List<T> {
    return collectDescendantsOfType({ true }, predicate)
}

inline fun <reified T : PsiElement> PsiElement.collectDescendantsOfType(
        crossinline canGoInside: (PsiElement) -> Boolean,
        noinline predicate: (T) -> Boolean = { true }
): List<T> {
    val result = ArrayList<T>()
    forEachDescendantOfType<T>(canGoInside) {
        if (predicate(it)) {
            result.add(it)
        }
    }
    return result
}

inline fun <reified T : PsiElement> PsiElement.forEachDescendantOfType(noinline action: (T) -> Unit) {
    forEachDescendantOfType({ true }, action)
}

inline fun <reified T : PsiElement> PsiElement.forEachDescendantOfType(
        crossinline canGoInside: (PsiElement) -> Boolean,
        noinline action: (T) -> Unit
) {
    this.accept(object : PsiRecursiveElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (canGoInside(element)) {
                super.visitElement(element)
            }

            if (element is T) {
                action(element)
            }
        }
    })
}