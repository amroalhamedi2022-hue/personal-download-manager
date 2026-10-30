plugins {
    `kotlin-dsl`
}
repositories {
    maven("https://maven-central.storage-download.googleapis.com/maven2/")
    mavenCentral()
    google()
}
version = 1
group = "ir.amirab.plugin"
dependencies {
    implementation(libs.pluginAndroidGradle)
    implementation(libs.handlebarsJava)
    implementation(libs.okio.okio)
}
