plugins{
    kotlin("multiplatform")
}
repositories{
    maven("https://maven-central.storage-download.googleapis.com/maven2/")
    mavenCentral()
}
kotlin {
    jvm("desktop")
}
version=1
group="ir.amirab.util"
