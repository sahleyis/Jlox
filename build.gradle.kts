plugins {
    application
    java
}

repositories {
    mavenCentral()
}

// Tell Gradle to treat 'src' as the root of your Java source files
sourceSets {
    named("main") {
        java {
            setSrcDirs(listOf("src"))
        }
    }
}

application {
    // Your main entry point class
    mainClass.set("com.craftinginterpreters.lox.Lox")
}

// Allow terminal stdin for the interactive REPL prompt
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}