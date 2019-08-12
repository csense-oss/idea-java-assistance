package csense.idea.java.assistance.quickfixes

//import com.intellij.ui.awt.*
import com.intellij.codeInspection.*
import com.intellij.openapi.application.*
import com.intellij.openapi.command.*
import com.intellij.openapi.editor.*
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.*
import com.intellij.openapi.ui.popup.*
import com.intellij.psi.*
import com.intellij.ui.awt.*
import com.intellij.util.*
import csense.idea.java.assistance.inspections.*


class MoveDeclarationsQuickFix(element: PsiClass) : LocalQuickFixOnPsiElement(element) {
    override fun getFamilyName(): String {
        return "csense - java assistant - fix declaration order for class"
    }

    override fun getText(): String {
        return "Rearrange items to avoid initialization order issues."
    }

    @Throws(IncorrectOperationException::class)
    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val asClass = startElement as? PsiClass ?: return
        val editor = startElement.findEditor() ?: return
        val ourFqName = asClass.fqName() ?: return
        //step 1 , find all non-delegating references,
        val nonDelegates: Array<PsiField> = asClass.fields
        //step 2 compute a DAG
        val deps = nonDelegates.computeDependencyDAG(ourFqName) ?: return //broken code.
        //step 3 do a topological sorting
        val sorted = deps.sortTopologically()
        if (sorted == null) {
            //step 3.1 if NO Cycles are there go on else report error.
            reportCyclicProblem(editor)
            return
        }
        //since idea does not like us to "remove and add" the same types, we instead creates copies.
        val newSorted = sorted.map { it.copy() }

        //step 4 modify class by removing all props and re-added them in the sorted list.
        project.executeWriteCommand(text) {
            nonDelegates.forEachIndexed { index, item ->
                item.replace(newSorted[index])
            }
        }


    }

    fun Project.executeWriteCommand(name: String, command: () -> Unit) {
        CommandProcessor.getInstance().executeCommand(this, {
            ApplicationManager.getApplication().runWriteAction(command)
        }, name, null)
    }


    fun PsiElement.findEditor(): Editor? {
        ApplicationManager.getApplication().assertReadAccessAllowed()
        if (!containingFile.isValid) return null
        val file = containingFile?.virtualFile ?: return null
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return null

        val editorFactory = EditorFactory.getInstance()

        val editors = editorFactory.getEditors(document)
        return if (editors.isEmpty()) null else editors[0]
    }

    private fun reportCyclicProblem(editor: Editor) {
        val htmlText = "Could not re-arrange as you have cyclic dependencies, which you have to resolve first."
        val messageType: MessageType = MessageType.ERROR

        val location: RelativePoint = JBPopupFactory.getInstance().guessBestPopupLocation(editor)

        JBPopupFactory.getInstance()
                .createHtmlTextBalloonBuilder(htmlText, messageType, null)
                .createBalloon()
                .show(location,
                        Balloon.Position.atRight)
    }

}


/*
L ← Empty list that will contain the sorted elements
S ← Set of all nodes with no incoming edge
while S is non-empty do
    remove a node n from S
    add n to tail of L
    for each node m with an edge e from n to m do
        remove edge e from the graph
        if m has no other incoming edges then
            insert m into S
if graph has edges then
    return error   (graph has at least one cycle)
else
    return L   (a topologically sorted order)
*/
private fun MutableVariableNameGraph.sortTopologically(): List<PsiField>? {
    val l = mutableListOf<PsiField>()
    val s = startingNodes.toMutableList()
    while (s.isNotEmpty()) {
        val element = s.removeAt(0)
        l.add(element.realProperty)
        val foundEdge = edges.remove(element) ?: continue
        foundEdge.forEach {
            it.dependsOn.remove(element)
            if (it.dependsOn.isEmpty()) {
                s.add(it)
            }
        }
    }

    //if leftovers => cyclic dependencies.
    return if (edges.isNotEmpty()) {
        null
    } else {
        l
    }

}

private fun Array<PsiField>.computeNameLookupToVariableNameDep(

): Map<String, MutableVariableNameDependencies> {
    val variableMap: MutableMap<String, MutableVariableNameDependencies> = mutableMapOf()
    this.forEach {
        val name = it.name ?: ""
        variableMap[name] = MutableVariableNameDependencies(it, name, mutableListOf())
    }
    return variableMap
}

private fun PsiReferenceExpression.addToGraph(
        prop: MutableVariableNameDependencies,
        variableMap: Map<String, MutableVariableNameDependencies>,
        graph: MutableVariableNameGraph
) {
    val itName = referenceName ?: return
    val refsTo = variableMap[itName]
            ?: return
    prop.dependsOn.add(refsTo) //I depend on
    graph.edges[refsTo]?.add(prop)
}

fun Array<PsiField>.computeDependencyDAG(
        ourFqName: String
): MutableVariableNameGraph? {
    val graph = MutableVariableNameGraph(mutableMapOf(), mutableListOf())
    val nonDelegatesQuickLookup = this.computeQuickIndexedNameLookup()
    val variableMap: Map<String, MutableVariableNameDependencies> =
            this.computeNameLookupToVariableNameDep()

    variableMap.forEach { entry: Map.Entry<String, MutableVariableNameDependencies> ->
        graph.edges[entry.value] = mutableListOf()
    }

    variableMap.forEach { (_, prop) ->
        val localRefs = prop.realProperty.findLocalReferencesForInitializer(
                ourFqName,
                nonDelegatesQuickLookup.keys)
        localRefs.forEach { ref ->
            ref.mainReference.addToGraph(prop, variableMap, graph)
            ref.innerReferences.forEach {
                it.addToGraph(prop, variableMap, graph)
            }
        }
    }
    //find starting nodes
    val startingNodes = variableMap.filter {
        it.value.dependsOn.isEmpty()
    }.map { it.value }
    graph.startingNodes.addAll(startingNodes)
    return graph
}

private fun Array<PsiField>.computeQuickIndexedNameLookup(): Map<String, Int> {
    val nonDelegatesQuickLookup: MutableMap<String, Int> = mutableMapOf()
    forEachIndexed { index, item ->
        val propName = item.name
        nonDelegatesQuickLookup[propName] = index
    }
    return nonDelegatesQuickLookup
}

data class MutableVariableNameGraph(
        val edges: MutableMap<MutableVariableNameDependencies, MutableList<MutableVariableNameDependencies>>,
        val startingNodes: MutableList<MutableVariableNameDependencies>
)

data class MutableVariableNameDependencies(
        val realProperty: PsiField,
        val name: String,
        val dependsOn: MutableList<MutableVariableNameDependencies>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MutableVariableNameDependencies

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}