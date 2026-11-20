package com.rosyidputrar.taskify.core.innertube.pages

import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Parser toleran `parseAccountInfo` (deep-scan `activeAccountHeaderRenderer`)
 * dan `parseSelectedAccount` dari `accounts_list`.
 * Fixture JSON minimal inline, data dummy.
 */
class AccountMenuParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun headerJson(name: String = "Nama Dummy") = """
        {"accountName": {"runs": [{"text": "$name"}]},
         "email": {"runs": [{"text": "dummy@example.com"}]},
         "channelHandle": {"runs": [{"text": "@dummyhandle"}]},
         "accountPhoto": {"thumbnails": [
           {"url": "https://example.com/photo-small.jpg", "width": 40, "height": 40},
           {"url": "https://example.com/photo-large.jpg", "width": 88, "height": 88}
         ]}}
    """.trimIndent()

    @Test
    fun `parser toleran menemukan header langsung di bawah root`() {
        val root = json.parseToJsonElement(
            """{"activeAccountHeaderRenderer": ${headerJson()}}""",
        )
        val account = parseAccountInfo(root)
        assertEquals("Nama Dummy", account?.name)
        assertEquals("dummy@example.com", account?.email)
        assertEquals("@dummyhandle", account?.channelHandle)
        assertEquals("https://example.com/photo-large.jpg", account?.thumbnailUrl)
    }

    @Test
    fun `parser toleran menemukan header di dalam contents`() {
        val root = json.parseToJsonElement(
            """{"contents": [{"section": {"activeAccountHeaderRenderer": ${headerJson("Nama Bersarang")}}}] }""",
        )
        assertEquals("Nama Bersarang", parseAccountInfo(root)?.name)
    }

    @Test
    fun `jalur tetap kosong dan deep-scan kosong menghasilkan null`() {
        val root = json.parseToJsonElement("""{"actions": [], "contents": [{"x": 1}]}""")
        assertNull(parseAccountInfo(root))
    }

    @Test
    fun `deep-scan menemukan header pertama bila ada dua`() {
        val root = json.parseToJsonElement(
            """{"a": {"activeAccountHeaderRenderer": ${headerJson("Pertama")}},
                "b": {"activeAccountHeaderRenderer": ${headerJson("Kedua")}}}""",
        )
        assertEquals("Pertama", parseAccountInfo(root)?.name)
    }

    @Test
    fun `selectedAccount memilih akun isSelected dari dua akun`() {
        val root = json.parseToJsonElement(ACCOUNTS_LIST_TWO)
        val selected = parseSelectedAccount(root)
        assertEquals("Akun Utama", selected?.info?.name)
        assertEquals("utama@example.com", selected?.info?.email)
        assertEquals("@utama", selected?.info?.channelHandle)
        assertEquals("https://example.com/utama-large.jpg", selected?.info?.thumbnailUrl)
        assertEquals("delegatedB||userB", selected?.dataSyncId)
    }

    @Test
    fun `selectedAccount mengabaikan akun disabled yang isSelected`() {
        val root = json.parseToJsonElement(ACCOUNTS_LIST_DISABLED_SELECTED)
        val selected = parseSelectedAccount(root)
        assertEquals("Akun Aktif", selected?.info?.name)
        assertEquals("delegatedAktif||userAktif", selected?.dataSyncId)
    }

    @Test
    fun `selectedAccount null bila tanpa akun terpilih`() {
        val root = json.parseToJsonElement(ACCOUNTS_LIST_NONE_SELECTED)
        assertNull(parseSelectedAccount(root))
    }

    @Test
    fun `selectedAccount null bila nama akun terpilih kosong`() {
        val root = json.parseToJsonElement(ACCOUNTS_LIST_SELECTED_NO_NAME)
        assertNull(parseSelectedAccount(root))
    }

    @Test
    fun `selectedAccount memakai nama simpleText bila runs tidak ada`() {
        val root = json.parseToJsonElement(ACCOUNTS_LIST_SIMPLE_TEXT)
        val selected = parseSelectedAccount(root)
        assertEquals("Akun Simple", selected?.info?.name)
        assertEquals("delegatedS||userS", selected?.dataSyncId)
    }

    @Test
    fun `selectedAccount memakai mainAppWebDataSyncId bila renderer tanpa serviceEndpoint`() {
        val root = json.parseToJsonElement(ACCOUNTS_LIST_NO_ENDPOINT)
        val selected = parseSelectedAccount(root)
        assertEquals("Akun Tanpa Endpoint", selected?.info?.name)
        assertEquals("delegatedMain||userMain", selected?.dataSyncId)
    }

    companion object {
        private const val ACCOUNTS_LIST_TWO = """
            {
              "contents": [
                {"accountItemRenderer": {
                  "accountName": {"runs": [{"text": "Akun Lain"}]},
                  "accountByline": {"runs": [{"text": "lain@example.com"}]},
                  "hasChannel": true, "isSelected": false,
                  "serviceEndpoint": {"dataSyncId": "delegatedA||userA"}
                }},
                {"accountItemRenderer": {
                  "accountName": {"runs": [{"text": "Akun Utama"}]},
                  "email": {"runs": [{"text": "utama@example.com"}]},
                  "channelHandle": {"runs": [{"text": "@utama"}]},
                  "accountPhoto": {"thumbnails": [
                    {"url": "https://example.com/utama-small.jpg", "width": 40, "height": 40},
                    {"url": "https://example.com/utama-large.jpg", "width": 88, "height": 88}
                  ]},
                  "hasChannel": true, "isSelected": true,
                  "serviceEndpoint": {"datasyncId": "delegatedB||userB"}
                }}
              ]
            }
        """

        private const val ACCOUNTS_LIST_DISABLED_SELECTED = """
            {
              "contents": [
                {"accountItemRenderer": {
                  "accountName": {"runs": [{"text": "Akun Nonaktif"}]},
                  "isDisabled": true, "isSelected": true,
                  "serviceEndpoint": {"dataSyncId": "delegatedOff||userOff"}
                }},
                {"accountItemRenderer": {
                  "accountName": {"runs": [{"text": "Akun Aktif"}]},
                  "isSelected": true,
                  "serviceEndpoint": {"dataSyncId": "delegatedAktif||userAktif"}
                }}
              ]
            }
        """

        private const val ACCOUNTS_LIST_NONE_SELECTED = """
            {
              "contents": [
                {"accountItemRenderer": {
                  "accountName": {"runs": [{"text": "Akun Pertama"}]},
                  "isSelected": false,
                  "serviceEndpoint": {"dataSyncId": "delegatedA||userA"}
                }},
                {"accountItemRenderer": {
                  "accountName": {"runs": [{"text": "Akun Kedua"}]},
                  "isSelected": false,
                  "serviceEndpoint": {"dataSyncId": "delegatedB||userB"}
                }}
              ]
            }
        """

        private const val ACCOUNTS_LIST_SELECTED_NO_NAME = """
            {
              "contents": [
                {"accountItemRenderer": {
                  "accountName": {"runs": []},
                  "isSelected": true,
                  "serviceEndpoint": {"dataSyncId": "delegatedX||userX"}
                }}
              ]
            }
        """

        private const val ACCOUNTS_LIST_SIMPLE_TEXT = """
            {
              "contents": [
                {"accountItemRenderer": {
                  "accountName": {"simpleText": "Akun Simple"},
                  "isSelected": true,
                  "serviceEndpoint": {"dataSyncId": "delegatedS||userS"}
                }}
              ]
            }
        """

        private const val ACCOUNTS_LIST_NO_ENDPOINT = """
            {
              "responseContext": {
                "mainAppWebResponseContext": {"dataSyncId": "delegatedMain||userMain"}
              },
              "contents": [
                {"accountItemRenderer": {
                  "accountName": {"runs": [{"text": "Akun Tanpa Endpoint"}]},
                  "isSelected": true
                }}
              ]
            }
        """
    }
}
