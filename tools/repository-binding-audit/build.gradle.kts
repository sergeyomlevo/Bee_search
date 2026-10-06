plugins { id("com.android.application") version "9.3.2" }

val copiedCore = layout.buildDirectory.dir("generated/repositoryCore")
abstract class CopyRepositoryCore : Sync() {
    @get:OutputDirectory abstract val generatedSources: DirectoryProperty
}
val copyCore by tasks.registering(CopyRepositoryCore::class) {
    generatedSources.set(copiedCore)
    from("../../app/src/main/java/org/beesearch/app/data/backuprepository") {
        include("*.kt")
        exclude("BackupDirectoryBootstrap.kt")
        into("org/beesearch/app/data/backuprepository")
    }
    into(generatedSources)
}
android {
    namespace = "org.beesearch.bindingaudit"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.beesearch.bindingaudit"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "Slice2A-audit"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
androidComponents.onVariants { variant ->
    variant.sources.java?.addGeneratedSourceDirectory(copyCore) { it.generatedSources }
}
dependencies {
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    // Matches the DataStore runtime's coroutines-core; required by this Activity's Main scope.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
