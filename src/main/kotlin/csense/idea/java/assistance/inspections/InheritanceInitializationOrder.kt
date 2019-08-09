//package csense.idea.java.assistance.inspections
//
//import com.intellij.codeHighlighting.*
//import com.intellij.codeInspection.*
//import csense.idea.java.assistance.*
//import org.jetbrains.kotlin.idea.core.*
//import org.jetbrains.kotlin.idea.refactoring.*
//import org.jetbrains.kotlin.idea.references.*
//import org.jetbrains.kotlin.js.resolve.diagnostics.*
//import org.jetbrains.kotlin.lexer.*
//import org.jetbrains.kotlin.psi.*
//import org.jetbrains.kotlin.psi.psiUtil.*
//
//class InheritanceInitializationOrder : AbstractBaseJavaLocalInspectionTool() {
//
//    override fun getDisplayName(): String {
//        return "Wrong use of initialization across inheritance"
//    }
//
//    override fun getStaticDescription(): String? {
//        return """
//            Since initialization in inheritance is non trivial, and the use of open / abstract methods / functions coupled with
//            field / init initialization, opens up the ability to screw up the JVM's initialization design.
//            The result is primitives could end up with their neutral value, and object references will be null.
//            These unforeseen consequences, does not necessarily manifest immediately, some can even appear to work, until a random event.
//        """.trimIndent()
//    }
//
//    override fun getDescriptionFileName(): String? {
//        return "more desc ? "
//    }
//
//    override fun getShortName(): String {
//        return "InheritanceInitializationOrder"
//    }
//
//    override fun getGroupDisplayName(): String {
//        return Constants.InspectionGroupName
//    }
//
//    override fun getDefaultLevel(): HighlightDisplayLevel {
//        return HighlightDisplayLevel.ERROR
//    }
//
//    override fun isEnabledByDefault(): Boolean {
//        return true
//    }
//
//
//    //we have 2 parts of this inspection
//    //part 1 is the base class "issue", where we are accessing / using abstract / open from fields or the init function.
//    //this can / may cause null due to the instantiation order for jvm.
//    //which is the second part of this inspection, to see if this is done at the usage site.
//
//    override fun buildVisitor(holder: ProblemsHolder,
//                              isOnTheFly: Boolean): KtVisitorVoid {
//        return classVisitor { ourClass: KtClass ->
//            val ourFqName = ourClass.fqName?.asString() ?: return@classVisitor
//            val isAbstractOrOpen = ourClass.isAbstractOrOpen()
//            val isInheriting = ourClass.superTypeListEntries.isNotEmpty()
//
//            if (!isInheriting && !isAbstractOrOpen) {
//                //we are not using inheritance bail out
//                return@classVisitor
//            }
//            //case child class
//            if (isInheriting) {
//                handleChildClass(ourClass, ourFqName, holder)
//            }
//            //case base class (potentially child as well)
//
//            if (isAbstractOrOpen) {
//                handleBaseClass(ourClass, ourFqName, holder)
//            }
//        }
//    }
//
//    fun handleBaseClass(ourClass: KtClass, ourFqName: String, holder: ProblemsHolder) {
//        computeBaseClassDangerousStarts(ourClass, ourFqName)
//                .forEach { (property, _) ->
//                    holder.registerProblem(property,
//                            "You are using a constructor provided argument for an overridden property\n" +
//                                    "This has the potential to cause a NullPointerException \n" +
//                                    "if the base class uses this in any initialization  (field or init)",
//                            ProblemHighlightType.WEAK_WARNING)
//                }
//    }
//
//    fun handleChildClass(ourClass: KtClass, ourFqName: String, holder: ProblemsHolder) {
//        val parentProblems = ourClass.superClass ?: return
//        val superProblems = computeBaseClassDangerousStarts(
//                parentProblems,
//                parentProblems.fqName?.asString() ?: "")
//
//
//        val constructorArguments = ourClass.getPrimaryConstructorParameterList() ?: return
//        val inputParameterNames = constructorArguments.parameters.mapNotNull {
//            it.name
//        }
//        val nonDelegates = ourClass.findNonDelegatingProperties()
//        val propertiesOverridden = nonDelegates.filter {
//            it.isOverriding()
//        }
//        val functions = ourClass.getAllFunctions()
//        val functionsOverriding = functions.filter {
//            it.isOverriding()
//        }
//
//        val toLookFor = (
//                inputParameterNames
//                ).toSet()
//
//
//        val superProblemsNames = superProblems.values.map {
//            it.map { name ->
//                name.getReferencedName()
//            }
//        }.flatten().toSet()
//
//        propertiesOverridden.forEach {
//            val usesConstructorParameterInOverridden = it.findLocalReferences(ourFqName, toLookFor)
//            if (usesConstructorParameterInOverridden.isNotEmpty() && superProblemsNames.contains(it.name ?: "")) {
//                holder.registerProblem(it,
//                        "You are using a constructor provided argument for an overridden property\n" +
//                                "This has the potential to cause a NullPointerException \n" +
//                                "if the base class uses this in any initialization  (field or init)")
//            }
//        }
//
//        functionsOverriding.forEach { function ->
//            val usesConstructorParameterInOverridden = function.findLocalReferences(ourFqName, toLookFor)
//            if (usesConstructorParameterInOverridden.isNotEmpty() && superProblemsNames.contains(function.name ?: "")) {
//                holder.registerProblem(function,
//                        "You are using a constructor provided argument for an overridden function.\n" +
//                                "This will cause a NullPointerException, since it is used in the base class initialization")
//            }
//        }
//
//
//    }
//
//    fun computeBaseClassDangerousStarts(ourClass: KtClass, ourFqName: String): Map<KtProperty, List<KtNameReferenceExpression>> {
//        val nonDelegates = ourClass.findNonDelegatingProperties()
//        val dangerousProperties = nonDelegates.filter {
//            it.isAbstractOrOpen()
//        }
//        val functions = ourClass.getAllFunctions()
//        val dangerousFunctions = functions.filter {
//            it.isAbstractOrOpen()
//        }
//
//        val namesToLookFor = (dangerousFunctions.map { it.name } + dangerousProperties.map { it.name }).filterNotNull()
//        val namesToLookForSet = namesToLookFor.toSet()
//
//        val resultingMap: MutableMap<KtProperty, List<KtNameReferenceExpression>> = mutableMapOf()
//        nonDelegates.forEach {
//            val references = it.findLocalReferences(ourFqName, namesToLookForSet)
//            if (references.isNotEmpty()) {
//                resultingMap[it] = references
//            }
//        }
//        return resultingMap
//    }
//}
//
//val KtClass.superClass: KtClass?
//    get() {
//        val superTypes = superTypeListEntries
//        if (superTypes.isEmpty()) {
//            return null
//        }
//        superTypes.forEach {
//            val realClass = it.typeAsUserType?.referenceExpression?.resolveMainReferenceToDescriptors()
//                    ?.firstOrNull()?.containingDeclaration?.findPsi() as? KtClass
//            if (realClass != null) {
//                return realClass
//            }
//        }
//        return null
//    }
//
//fun KtProperty.isOverriding(): Boolean {
//    return modifierList?.isOverriding() ?: false
//}
//
//fun KtNamedFunction.isOverriding(): Boolean {
//    return modifierList?.isOverriding() ?: false
//}
//
//fun KtModifierList.isOverriding(): Boolean {
//    return hasModifier(KtTokens.OVERRIDE_KEYWORD)
//}
//
//fun KtClass.isAbstractOrOpen(): Boolean {
//    return isAbstract() || isOverridable()
//}
//
//fun KtNamedFunction.isAbstractOrOpen(): Boolean {
//    return isAbstract() || isOverridable()
//}
//
//fun KtProperty.isAbstractOrOpen(): Boolean {
//    return isAbstract() || isOverridable()
//}
//
//fun KtClass.getAllFunctions(): List<KtNamedFunction> = collectDescendantsOfType()