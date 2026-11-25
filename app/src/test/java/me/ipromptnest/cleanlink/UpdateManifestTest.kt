package me.ipromptnest.cleanlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 清单是唯一从外部拿到的数据，坏了就直接放弃更新，绝不半信半疑地往下走。
 */
class UpdateManifestTest {

    private val good = """
        {
          "code": 2,
          "name": "1.0.1",
          "notes": "更新检查不再联网展开短链",
          "size": 13100000,
          "sha256": "ABCDEF12",
          "signer": "9F8E7D",
          "urls": ["https://a.example/x.apk", "https://b.example/x.apk"]
        }
    """.trimIndent()

    @Test
    fun parsesCompleteManifest() {
        val info = UpdateManifest.parse(good)
        assertNotNull(info)
        info!!
        assertEquals(2, info.code)
        assertEquals("1.0.1", info.name)
        assertEquals("更新检查不再联网展开短链", info.notes)
        assertEquals(13_100_000L, info.size)
        assertEquals(2, info.urls.size)
    }

    @Test
    fun normalizesDigestsToLowerCase() {
        val info = UpdateManifest.parse(good)!!
        assertEquals("abcdef12", info.sha256)
        assertEquals("9f8e7d", info.signer)
    }

    @Test
    fun rejectsMissingCode() {
        assertNull(UpdateManifest.parse("""{"name":"1.0.1","urls":["https://a/x.apk"]}"""))
        assertNull(UpdateManifest.parse("""{"code":0,"name":"1.0.1","urls":["https://a/x.apk"]}"""))
    }

    @Test
    fun rejectsMissingName() {
        assertNull(UpdateManifest.parse("""{"code":2,"urls":["https://a/x.apk"]}"""))
        assertNull(UpdateManifest.parse("""{"code":2,"name":"   ","urls":["https://a/x.apk"]}"""))
    }

    @Test
    fun rejectsManifestWithoutUsableUrl() {
        assertNull(UpdateManifest.parse("""{"code":2,"name":"1.0.1"}"""))
        assertNull(UpdateManifest.parse("""{"code":2,"name":"1.0.1","urls":[]}"""))
        assertNull(UpdateManifest.parse("""{"code":2,"name":"1.0.1","urls":["ftp://a/x.apk","weixin://dl"]}"""))
    }

    @Test
    fun acceptsSingleUrlField() {
        val info = UpdateManifest.parse("""{"code":3,"name":"1.0.2","url":"https://a/x.apk"}""")
        assertEquals(listOf("https://a/x.apk"), info?.urls)
    }

    @Test
    fun dedupesUrls() {
        val info = UpdateManifest.parse(
            """{"code":2,"name":"1.0.1","urls":["https://a/x.apk","https://a/x.apk"],"url":"https://a/x.apk"}""",
        )
        assertEquals(1, info?.urls?.size)
    }

    @Test
    fun toleratesMissingOptionalFields() {
        val info = UpdateManifest.parse("""{"code":2,"name":"1.0.1","urls":["https://a/x.apk"]}""")!!
        assertEquals("", info.sha256)
        assertEquals("", info.signer)
        assertEquals(0L, info.size)
        assertEquals("", info.notes)
    }

    @Test
    fun rejectsBrokenJson() {
        assertNull(UpdateManifest.parse("not json at all"))
        assertNull(UpdateManifest.parse("""{"code":2,"name":"1.0.1","urls":["https://a/x.apk","""))
        assertNull(UpdateManifest.parse(""))
    }

    @Test
    fun negativeSizeIsClampedToZero() {
        val info = UpdateManifest.parse("""{"code":2,"name":"1.0.1","size":-5,"urls":["https://a/x.apk"]}""")
        assertEquals(0L, info?.size)
    }
}
