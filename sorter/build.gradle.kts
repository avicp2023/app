plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

group = "com.elchanan.rhythm"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions { jvmTarget = "17" }
}

application {
    mainClass.set("com.elchanan.rhythm.desktop.sorter.FolderSorterKt")
    applicationName = "rhythm-sorter"
}

val onnxRuntime by configurations.creating

val onnxRuntimeTrimmed = tasks.register<Jar>("onnxRuntimeTrimmed") {
    archiveFileName.set("onnxruntime-1.26.0-trimmed.jar")
    destinationDirectory.set(layout.buildDirectory.dir("onnxruntime"))
    from({ onnxRuntime.map { zipTree(it) } }) {
        exclude("ai/onnxruntime/native/osx-*/**")
        exclude("ai/onnxruntime/native/linux-aarch64/**")
        exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    implementation(project(":engine"))
    implementation(files(onnxRuntimeTrimmed))
    onnxRuntime("com.microsoft.onnxruntime:onnxruntime:1.26.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("com.tagtraum:ffsampledsp-complete:0.9.56")
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
    implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
    implementation("com.googlecode.soundlibs:tritonus-share:0.3.7.4")
    implementation("org.jflac:jflac-codec:1.5.2")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.register<Exec>("packageDeb") {
    dependsOn(tasks.named("installDist"))
    val destination = layout.buildDirectory.dir("distributions").get().asFile
    doFirst { destination.mkdirs() }
    commandLine(
        "jpackage",
        "--type", "deb",
        "--name", "rhythm-sorter",
        "--app-version", project.version.toString(),
        "--vendor", "Rhythm Sorter",
        "--description", "Hebrew desktop tool to sort music by mood",
        "--input", layout.buildDirectory.dir("install/rhythm-sorter/lib").get().asFile.absolutePath,
        "--main-jar", "sorter-1.0.0.jar",
        "--main-class", "com.elchanan.rhythm.desktop.sorter.FolderSorterKt",
        "--dest", destination.absolutePath,
        "--linux-shortcut"
    )
}
