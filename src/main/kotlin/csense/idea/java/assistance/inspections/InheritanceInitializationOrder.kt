package csense.idea.java.assistance.inspections

import com.intellij.codeHighlighting.*
import com.intellij.codeInspection.*
import com.intellij.psi.*
import csense.idea.java.assistance.*
import csense.idea.java.assistance.suppression.*
import csense.idea.java.assistance.visitors.*

class InheritanceInitializationOrder : LocalInspectionTool(), CustomSuppressableInspectionTool {

    override fun getDisplayName(): String {
        return "Wrong use of initialization across inheritance"
    }

    override fun getStaticDescription(): String? {
        return """
            Since initialization in inheritance is non trivial, and the use of open / abstract methods / functions coupled with
            field / init initialization, opens up the ability to screw up the JVM's initialization design.
            The result is primitives could end up with their neutral value, and object references will be null.
            These unforeseen consequences, does not necessarily manifest immediately, some can even appear to work, until a random event.
        """.trimIndent()
    }

    override fun getDescriptionFileName(): String? {
        return "more desc ? "
    }

    override fun getShortName(): String {
        return "InheritanceInitializationOrder"
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
                PsiFieldMethodSuppressor("Suppress naming mismatch issue", groupDisplayName, shortName))
    }


    //we have 2 parts of this inspection
    //part 1 is the base class "issue", where we are accessing / using abstract / open from fields or the init function.
    //this can / may cause null due to the instantiation order for jvm.
    //which is the second part of this inspection, to see if this is done at the usage site.

    override fun buildVisitor(holder: ProblemsHolder,
                              isOnTheFly: Boolean): PsiElementVisitor {
        return PsiClassVisitor { ourClass: PsiClass ->
            val ourFqName = ourClass.name ?: return@PsiClassVisitor
            val isAbstractOrOpen = ourClass.isAbstractOrNotFinal()
            val isInheriting = ourClass.superClassType != null

            if (!isInheriting && !isAbstractOrOpen) {
                //we are not using inheritance bail out
                return@PsiClassVisitor
            }
            //case child class
            if (isInheriting) {
                handleChildClass(ourClass, ourFqName, holder)
            }
            //case base class (potentially child as well)

            if (isAbstractOrOpen) {
                handleBaseClass(ourClass, ourFqName, holder)
            }
        }
    }

    fun handleBaseClass(ourClass: PsiClass, ourFqName: String, holder: ProblemsHolder) {
        computeBaseClassDangerousStarts(ourClass, ourFqName)
                .forEach { (property, _) ->
                    holder.registerProblem(property,
                            "You are using a constructor provided argument for an overridden property\n" +
                                    "This has the potential to cause a NullPointerException \n" +
                                    "if the base class uses this in any initialization  (field or init)",
                            ProblemHighlightType.WEAK_WARNING)
                }
    }

    fun handleChildClass(ourClass: PsiClass, ourFqName: String, holder: ProblemsHolder) {
        val parentProblems = ourClass.superClass ?: return
        val superProblems = computeBaseClassDangerousStarts(
                parentProblems,
                parentProblems.name ?: "")

        //HMMMMMMMMMMMMMMMMMMMM ????????????????
        val constructorArguments = ourClass.constructors.firstOrNull() ?: return
        val inputParameterNames = constructorArguments.parameters.mapNotNull {
            it.name
        }
        val nonDelegates = ourClass.fields
        val propertiesOverridden = nonDelegates.filter {
            it.isOverriding()
        }
        val functions = ourClass.methods
        val functionsOverriding = functions.filter {
            it.isOverriding()
        }

        val toLookFor = (
                inputParameterNames
                ).toSet()


        val superProblemsNames = superProblems.values.map {
            it.mapNotNull { name ->
                name.referenceName
            }
        }.flatten().toSet()

        propertiesOverridden.forEach {
            val usesConstructorParameterInOverridden = it.findLocalReferences(ourFqName, toLookFor)
            if (usesConstructorParameterInOverridden.isNotEmpty() && superProblemsNames.contains(it.name)) {
                holder.registerProblem(it,
                        "You are using a constructor provided argument for an overridden property\n" +
                                "This has the potential to cause a NullPointerException \n" +
                                "if the base class uses this in any initialization  (field or init)")
            }
        }

        functionsOverriding.forEach { function ->
            val usesConstructorParameterInOverridden = function.findLocalReferences(ourFqName, toLookFor)
            if (usesConstructorParameterInOverridden.isNotEmpty() && superProblemsNames.contains(function.name)) {
                holder.registerProblem(function,
                        "You are using a constructor provided argument for an overridden function.\n" +
                                "This will cause a NullPointerException, since it is used in the base class initialization")
            }
        }


    }

    fun computeBaseClassDangerousStarts(ourClass: PsiClass, ourFqName: String): Map<PsiField, List<PsiReferenceExpression>> {
        val nonDelegates = ourClass.fields
        val dangerousProperties = nonDelegates.filter {
            it.isAbstractOrNotFinal()
        }
        val functions = ourClass.methods
        val dangerousFunctions = functions.filter {
            it.isAbstractOrNotFinal()
        }

        val namesToLookFor = (dangerousFunctions.map { it.name } + dangerousProperties.map { it.name }).filterNotNull()
        val namesToLookForSet = namesToLookFor.toSet()

        val resultingMap: MutableMap<PsiField, List<PsiReferenceExpression>> = mutableMapOf()
        nonDelegates.forEach {
            val references = it.findLocalReferences(ourFqName, namesToLookForSet)
            if (references.isNotEmpty()) {
                resultingMap[it] = references
            }
        }
        return resultingMap
    }
}

fun PsiMethod.isAbstractOrNotFinal(): Boolean {
    return true
}

fun PsiField.isAbstractOrNotFinal(): Boolean {
    return true
}

fun PsiClass.isAbstractOrNotFinal(): Boolean {
    return true
}

fun PsiField.isOverriding(): Boolean {
    return true
}

fun PsiMethod.isOverriding(): Boolean {
    return true
}