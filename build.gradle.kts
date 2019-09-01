plugins {
    id("org.jetbrains.intellij") version "0.4.10"
    kotlin("jvm") version "1.3.50"
    java
    id("org.owasp.dependencycheck") version "5.1.0"
}

group = "csense-idea"
version = "0.4"

intellij {
    updateSinceUntilBuild = false //Disables updating since-build attribute in plugin.xml
    setPlugins("kotlin")
    version = "2018.2"
}

repositories {
    jcenter()
    //until ds is in jcenter
    maven(url = "https://dl.bintray.com/csense-oss/csense-kotlin")
}

dependencies {
    compile("csense.kotlin:csense-kotlin-jvm:0.0.21")
    compile("csense.kotlin:csense-kotlin-ds-jvm:0.0.21")
}
tasks.getByName<org.jetbrains.intellij.tasks.PatchPluginXmlTask>("patchPluginXml") {
    changeNotes("""
        <ul>
            <li>Fixed issue with mismatched names where some parameters did not have a name (eg "a, 42, b")</li>
            <li>Fixed issues with values from other classes ect. </li>
        </ul>
      """)
}

tasks.getByName("check").dependsOn("dependencyCheckAnalyze")
