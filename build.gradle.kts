plugins {
    application
}

group = "com.artandmagic"
version = "1.0-SNAPSHOT"
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
