package app.dropduo.android

import org.junit.Assert.*
import org.junit.Test

class UpdateReleaseTest {
    private fun release(version: String = "0.1.10", host: String = UpdateRelease.REPOSITORY, size: Long = 123) = """
        {"tag_name":"v$version","draft":false,"prerelease":false,"body":"Fixes & improvements",
         "assets":[{"name":"dropduo-android-v$version.apk","size":$size,"browser_download_url":"$host/releases/download/v$version/dropduo-android-v$version.apk"},
                   {"name":"SHA256SUMS","browser_download_url":"$host/releases/download/v$version/SHA256SUMS"}]}
    """.trimIndent()

    @Test fun numericVersionsAndPrereleaseStagesAreOrdered() {
        assertTrue(AppVersion.parse("0.1.10") > AppVersion.parse("0.1.9"))
        val versions = listOf("0.1.0-alpha.1", "0.1.0-alpha.10", "0.1.0-beta.1", "0.1.0-rc.1", "0.1.0", "0.2.0", "1.0.0")
        versions.zipWithNext().forEach { (a, b) -> assertTrue(AppVersion.parse(a) < AppVersion.parse(b)) }
        listOf("01.0.0", "1.0", "v1.0.0", "1.0.0-alpha.0", "1.0.0/evil", "-1.0.0", "99999999999999999999.0.0").forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { AppVersion.parse(invalid) }
        }
    }

    @Test fun onlyANewerReleaseIsOffered() {
        assertEquals("0.1.10", UpdateRelease.parse(release(), "0.1.9")?.version)
        assertNull(UpdateRelease.parse(release(), "0.1.10"))
        assertNull(UpdateRelease.parse(release(), "0.2.0"))
        assertEquals("0.1.10", UpdateRelease.parse(release(), "0.1.10-rc.1")?.version)
    }

    @Test fun otherRepositoriesDraftsAndOversizedAssetsAreRejected() {
        listOf(release(host = "https://example.com"), release(size = 0), release(size = UpdateRelease.MAX_APK_BYTES + 1),
            release().replace("\"draft\":false", "\"draft\":true"), release().replace("\"prerelease\":false", "\"prerelease\":true"),
            release().replace("SHA256SUMS", "other-file"), release().replace("v0.1.10", "v../evil")).forEach { invalid ->
            assertThrows(Exception::class.java) { UpdateRelease.parse(invalid, "0.1.9") }
        }
    }

    @Test fun checksumMustMatchExactlyOneAsset() {
        val hash = "a".repeat(64)
        val line = "$hash  dropduo-android-v0.1.10.apk"
        assertEquals(hash, UpdateRelease.checksum(line + "\n" + "b".repeat(64) + "  mac.zip\n", "dropduo-android-v0.1.10.apk"))
        listOf("", "$line\n$line", "${"z".repeat(64)}  dropduo-android-v0.1.10.apk", "$hash  ../dropduo-android-v0.1.10.apk").forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { UpdateRelease.checksum(invalid, "dropduo-android-v0.1.10.apk") }
        }
    }
}
