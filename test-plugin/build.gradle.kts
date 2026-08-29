plugins {
    `java-library`
}

dependencies {
    compileOnly(project(":nix-api"))
}

tasks.jar {
    archiveBaseName = "nix-content-system-test-plugin"
}
