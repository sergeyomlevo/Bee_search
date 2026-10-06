// JVM plugin marker is not cached; the already installed official KGP is.
buildscript {
    repositories { gradlePluginPortal(); mavenCentral() }
    dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10") }
}
apply(plugin = "org.jetbrains.kotlin.jvm")
repositories { mavenCentral() }
dependencies {
    add("implementation", "com.google.code.gson:gson:2.11.0")
    add("testImplementation", "junit:junit:4.13.2")
}
extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> { jvmToolchain(17) }
tasks.named<Test>("test") {
    useJUnit(); maxHeapSize = "512m"
    val runtimePaths = configurations.getByName("runtimeClasspath")
    val launchClasspath = layout.buildDirectory.file("launch-classpath.txt")
    outputs.file(launchClasspath)
    doLast {
        launchClasspath.get().asFile.writeText(
            files(layout.buildDirectory.dir("classes/kotlin/main"), runtimePaths).asPath)
    }
}
