package csense.idea.java.assistance.notification

import com.intellij.openapi.components.*
import csense.kotlin.logger.*


class MainNotificationComponent : ProjectComponent {

    override fun projectOpened() {}

    override fun projectClosed() {}

    override fun initComponent() {
        L.usePrintAsLoggers()
        L.isLoggingAllowed(true)
    }


    override fun disposeComponent() {}

    override fun getComponentName(): String {
        return CUSTOM_NOTIFICATION_COMPONENT
    }

    companion object {
        private const val CUSTOM_NOTIFICATION_COMPONENT =
                "Csense - java assistance"
    }
}
