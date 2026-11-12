package com.uchet.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Разбор ответа GitHub /releases/latest (org.json — из Android, поэтому Robolectric). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UpdateCheckerTest {

    private fun release(tag: String, assetName: String = "uchet-42.apk") = """
        {
          "tag_name": "$tag",
          "name": "Сборка 42",
          "body": "Автодобавление трубы",
          "assets": [
            {"name": "notes.txt", "browser_download_url": "https://example.invalid/notes.txt", "size": 10},
            {"name": "$assetName", "browser_download_url": "https://example.invalid/$assetName", "size": 24349626}
          ]
        }
    """.trimIndent()

    @Test
    fun parsesBuildNumberAndApk() {
        val r = UpdateChecker.parseRelease(release("build-42"))!!
        assertEquals(42, r.versionCode)
        assertEquals("Сборка 42", r.title)
        assertEquals("Автодобавление трубы", r.notes)
        assertEquals("https://example.invalid/uchet-42.apk", r.apkUrl)
        assertEquals(24349626L, r.apkSize)
    }

    @Test
    fun ignoresForeignTagsMissingApkAndGarbage() {
        assertNull(UpdateChecker.parseRelease(release("v1.0")))
        assertNull(UpdateChecker.parseRelease(release("build-x")))
        assertNull(UpdateChecker.parseRelease(release("build-42", assetName = "uchet.zip")))
        assertNull(UpdateChecker.parseRelease("not json"))
        assertNull(UpdateChecker.parseRelease("{}"))
    }
}
