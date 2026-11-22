plugins {
    application
}

group = "com.artandmagic"
version = "1.0.0"
description = "Java port of the Amiga game AGONY by Art & Magic (Psygnosis, 1992)."

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

application {
    mainClass = "com.artandmagic.agony.Main"
}

tasks.named<JavaExec>("run") {
    // Run from the project folder so ../adf and ../sources are found.
    workingDir = projectDir
}

tasks.jar {
    manifest {
        attributes("Main-Class" to application.mainClass)
    }
}

// A self contained release made with jpackage: the game and a trimmed Java
// runtime, no game data.  The disks are downloaded on the first start.
//   gradle release                 -> build/release/Agony-<version>-<os>.zip
//   gradle jpackage -Ptype=msi     -> an installer instead (needs WiX on Windows)
val appName = "Agony"
val appVersion = version.toString().removeSuffix("-SNAPSHOT")
val osName = System.getProperty("os.name").lowercase().let {
    when {
        it.contains("win") -> "windows"
        it.contains("mac") -> "macos"
        else -> "linux"
    }
}
val jpackageDir = layout.buildDirectory.dir("jpackage")

val jpackage = tasks.register<Exec>("jpackage") {
    group = "distribution"
    description = "Packages the game with its own Java runtime."
    dependsOn(tasks.jar)
    val type = (findProperty("type") ?: "app-image").toString()
    val jar = tasks.jar.get().archiveFile.get().asFile
    val out = jpackageDir.get().asFile
    inputs.file(jar)
    outputs.dir(out)
    doFirst { delete(out) }
    val tool = File(System.getProperty("java.home"), "bin/jpackage").path
    commandLine(listOf(
        tool,
        "--type", type,
        "--name", appName,
        "--app-version", appVersion,
        "--vendor", "agony-java",
        "--description", project.description,
        "--input", jar.parentFile.path,
        "--main-jar", jar.name,
        "--main-class", application.mainClass.get(),
        // java.desktop for the window and the sound, java.net.http and the
        // elliptic curves of TLS for the download of the disks.
        "--add-modules", "java.desktop,java.net.http,jdk.crypto.ec",
        "--jlink-options", "--strip-debug --no-man-pages --no-header-files",
        "--dest", out.path
    ) + if (type != "app-image" && osName == "windows")
        listOf("--win-menu", "--win-shortcut", "--win-dir-chooser") else listOf())
}

tasks.register<Zip>("release") {
    group = "distribution"
    description = "Zips the packaged game, ready to hand out."
    dependsOn(jpackage)
    from(jpackageDir)
    archiveFileName = "$appName-$appVersion-$osName-x64.zip"
    destinationDirectory = layout.buildDirectory.dir("release")
}

// Grabs frames without opening a window:
//   gradle screenshot -Pframes=600 -Pout=shot.png [-Pmode=menu]
tasks.register<JavaExec>("screenshot") {
    group = "application"
    description = "Renders a frame of the port to a PNG file."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.artandmagic.agony.Screenshot"
    workingDir = projectDir
    args = listOfNotNull(
        (findProperty("frames") ?: "600").toString(),
        (findProperty("out") ?: "shot.png").toString(),
        findProperty("mode")?.toString()
    )
}
