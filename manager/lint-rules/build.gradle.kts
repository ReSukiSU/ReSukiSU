plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly("com.android.tools.lint:lint-api:32.2.1")
    compileOnly("com.android.tools.lint:lint-checks:32.2.1")
}

tasks.jar {
    manifest {
        attributes("Lint-Registry-v2" to "com.resukisu.resukisu.lint.ResukisuIssueRegistry")
    }
}
