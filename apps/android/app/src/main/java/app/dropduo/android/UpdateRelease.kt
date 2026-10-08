package app.dropduo.android

import com.google.gson.JsonParser

/** Release input is untrusted: only our repository's exact versioned assets are accepted. */
data class UpdateRelease(val version: String, val notes: String, val apkUrl: String, val size: Long, val checksumsUrl: String) {
    val filename get() = "dropduo-android-v$version.apk"

    companion object {
        const val REPOSITORY = "https://github.com/rohit-yadavv/dropduo"
        const val API = "https://api.github.com/repos/rohit-yadavv/dropduo/releases/latest"
        const val MAX_APK_BYTES = 512L * 1024 * 1024

        fun parse(json: String, installed: String): UpdateRelease? {
            val release = JsonParser.parseString(json).asJsonObject
            require(!release.get("draft").asBoolean && !release.get("prerelease").asBoolean) { "Not a published release" }
            val tag = release.get("tag_name").asString
            require(tag.startsWith("v") && tag.length <= 100) { "Invalid release version" }
            val version = tag.removePrefix("v")
            if (AppVersion.parse(version) <= AppVersion.parse(installed)) return null
            val filename = "dropduo-android-v$version.apk"
            val assets = release.getAsJsonArray("assets").map { it.asJsonObject }
            val apk = assets.single { it.get("name").asString == filename }
            val sums = assets.single { it.get("name").asString == "SHA256SUMS" }
            val base = "$REPOSITORY/releases/download/$tag/"
            require(apk.get("browser_download_url").asString == base + filename &&
                sums.get("browser_download_url").asString == base + "SHA256SUMS") { "Unexpected download location" }
            val size = apk.get("size").asLong
            require(size in 1..MAX_APK_BYTES) { "Invalid download size" }
            return UpdateRelease(version, release.get("body")?.takeUnless { it.isJsonNull }?.asString.orEmpty().take(32_000), base + filename, size, base + "SHA256SUMS")
        }

        fun checksum(text: String, filename: String): String {
            val matches = text.lineSequence().mapNotNull { line ->
                Regex("^([a-fA-F0-9]{64})  ([^/\\\\]+)$").matchEntire(line)?.let { it.groupValues[1] to it.groupValues[2] }
            }.filter { it.second == filename }.toList()
            require(matches.size == 1) { "Missing or ambiguous update checksum" }
            return matches.single().first.lowercase()
        }
    }
}

/** Matches versions accepted by scripts/check-release, including ordered alpha/beta/rc builds. */
data class AppVersion(private val parts: List<Long>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        parts.zip(other.parts).forEach { (a, b) -> if (a != b) return a.compareTo(b) }
        return 0
    }
    companion object {
        fun parse(value: String): AppVersion {
            val match = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-(alpha|beta|rc)\\.([1-9][0-9]*))?$").matchEntire(value)
                ?: throw IllegalArgumentException("Invalid app version")
            val stage = when (match.groupValues[4]) { "alpha" -> 0L; "beta" -> 1L; "rc" -> 2L; else -> 3L }
            return AppVersion(match.groupValues.slice(1..3).map { it.toLong() } + listOf(stage, match.groupValues[5].ifEmpty { "0" }.toLong()))
        }
    }
}
