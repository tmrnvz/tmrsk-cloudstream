rootProject.name = "CloudstreamPlugins"

val disabled = listOf("ExampleProvider", "Migration", "Famelack")

File(rootDir, ".").eachDir { dir ->
    if (!disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) include(dir.name)
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}

include("CanliYayin")
project(":CanliYayin").projectDir = file("ExampleProvider")

include("DunyaTV")
project(":DunyaTV").projectDir = file("Famelack")
