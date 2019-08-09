package csense.idea.java.assistance.inspections

import com.intellij.codeInspection.*

class InspectionsProvider : InspectionToolProvider {
    override fun getInspectionClasses(): Array<Class<*>> {
        return arrayOf(
//                InitializationOrder::class.java,
                NamedArgsPositionMismatch::class.java)
//                InheritanceInitializationOrder::class.java)
    }
}