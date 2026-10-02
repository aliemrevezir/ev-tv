// Android'e bağımlı olmayan mantık. Kendi başına derlenir ve test edilir:
//   gradle -p app/cekirdek test
// Uygulama bu derlemeyi includeBuild ile kullanır.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "cekirdek"
