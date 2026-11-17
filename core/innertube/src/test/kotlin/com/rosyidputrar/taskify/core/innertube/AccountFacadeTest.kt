package com.rosyidputrar.taskify.core.innertube

import com.rosyidputrar.taskify.core.innertube.pages.parseAccountDataSyncId
import com.rosyidputrar.taskify.core.innertube.pages.parseAccountInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fasad akun `YouTube.accountInfo()` / `accountDataSyncId()` via MockEngine.
 * Fixture JSON minimal inline (data dummy, bukan cookie/respons asli):
 * akun lengkap, varian tanpa akun, dan `accounts_list` dengan beberapa akun.
 */
class AccountFacadeTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun youtubeWith(menuJson: String, channelsJson: String, onRequest: ((String) -> Unit)? = null): YouTube {
        val engine = MockEngine { request ->
            onRequest?.invoke(request.url.encodedPath)
            val body = when {
                request.url.encodedPath.endsWith("/account/account_menu") -> menuJson
                request.url.encodedPath.endsWith("/account/accounts_list") -> channelsJson
                else -> """{}"""
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return YouTube(
            InnerTube(
                HttpClient(engine) {
                    expectSuccess = true
                    defaultRequest { url("https://music.youtube.com/youtubei/v1/") }
                },
            ),
        )
    }

    @Test
    fun `accountInfo mengembalikan akun lengkap dari account_menu`() = runTest {
        val youTube = youtubeWith(ACCOUNT_MENU_OK, ACCOUNTS_LIST_EMPTY)
        val result = youTube.accountInfo(
            authState = PlaybackAuthState(cookie = "SAPISID=dummy-sapisid; LOGIN_INFO=dummy-login"),
        )
        assertTrue(result.isSuccess)
        val account = result.getOrThrow()
        assertEquals("Nama Dummy", account.name)
        assertEquals("dummy@example.com", account.email)
        assertEquals("@dummyhandle", account.channelHandle)
        assertEquals("https://example.com/photo-large.jpg", account.thumbnailUrl)
    }

    @Test
    fun `accountInfo gagal bila respons tanpa akun aktif`() = runTest {
        val youTube = youtubeWith(ACCOUNT_MENU_NO_ACCOUNT, ACCOUNTS_LIST_EMPTY)
        val result = youTube.accountInfo()
        assertTrue(result.isFailure)
    }

    @Test
    fun `accountInfo gagal bila nama akun kosong`() = runTest {
        // Parser langsung: header ada tapi accountName tanpa runs.
        val root = json.parseToJsonElement(ACCOUNT_MENU_NO_NAME)
        assertNull(parseAccountInfo(root))
        val youTube = youtubeWith(ACCOUNT_MENU_NO_NAME, ACCOUNTS_LIST_EMPTY)
        assertTrue(youTube.accountInfo().isFailure)
    }

    @Test
    fun `accountDataSyncId memilih akun selected dan mengabaikan yang disabled`() = runTest {
        val youTube = youtubeWith(ACCOUNT_MENU_OK, ACCOUNTS_LIST_SELECTED)
        val result = youTube.accountDataSyncId()
        assertTrue(result.isSuccess)
        assertEquals("delegatedB||userB", result.getOrThrow())
    }

    @Test
    fun `accountDataSyncId memakai mainAppWebDataSyncId bila ada`() = runTest {
        val youTube = youtubeWith(ACCOUNT_MENU_OK, ACCOUNTS_LIST_MAIN)
        val result = youTube.accountDataSyncId()
        assertTrue(result.isSuccess)
        assertEquals("delegatedMain||userMain", result.getOrThrow())
    }

    @Test
    fun `accountDataSyncId tanpa selected mengambil yang pertama`() = runTest {
        val youTube = youtubeWith(ACCOUNT_MENU_OK, ACCOUNTS_LIST_NONE_SELECTED)
        val result = youTube.accountDataSyncId()
        assertTrue(result.isSuccess)
        assertEquals("delegatedA||userA", result.getOrThrow())
    }

    @Test
    fun `accountDataSyncId gagal bila tidak ada dataSyncId`() = runTest {
        val youTube = youtubeWith(ACCOUNT_MENU_OK, ACCOUNTS_LIST_EMPTY)
        val result = youTube.accountDataSyncId()
        assertTrue(result.isFailure)
        // Parser langsung: respons kosong → null.
        assertNull(parseAccountDataSyncId(json.parseToJsonElement(ACCOUNTS_LIST_EMPTY)))
    }

    @Test
    fun `accountInfo memanggil endpoint account_menu`() = runTest {
        val paths = mutableListOf<String>()
        val youTube = youtubeWith(ACCOUNT_MENU_OK, ACCOUNTS_LIST_EMPTY) { paths += it }
        assertTrue(youTube.accountInfo().isSuccess)
        assertEquals(listOf("/youtubei/v1/account/account_menu"), paths)
    }

    @Test
    fun `accountDataSyncId memanggil endpoint accounts_list`() = runTest {
        val paths = mutableListOf<String>()
        val youTube = youtubeWith(ACCOUNT_MENU_OK, ACCOUNTS_LIST_SELECTED) { paths += it }
        assertTrue(youTube.accountDataSyncId().isSuccess)
        assertEquals(listOf("/youtubei/v1/account/accounts_list"), paths)
    }

    @Test
    fun `parser accountInfo tanpa email dan handle tetap sukses`() = runTest {
        val root = json.parseToJsonElement(
            """
            {"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{"header":{
            "activeAccountHeaderRenderer":{
            "accountName":{"runs":[{"text":"Nama Saja"}]},
            "accountPhoto":{"thumbnails":[{"url":"https://example.com/p.jpg","width":40,"height":40}]}
            }}}}}}]}
            """.trimIndent(),
        )
        val account = parseAccountInfo(root)
        assertEquals("Nama Saja", account?.name)
        assertNull(account?.email)
        assertNull(account?.channelHandle)
        assertEquals("https://example.com/p.jpg", account?.thumbnailUrl)
    }

    companion object {
        private const val ACCOUNT_MENU_OK = """
            {
              "actions": [
                {
                  "openPopupAction": {
                    "popup": {
                      "multiPageMenuRenderer": {
                        "header": {
                          "activeAccountHeaderRenderer": {
                            "accountName": {"runs": [{"text": "Nama Dummy"}]},
                            "email": {"runs": [{"text": "dummy@example.com"}]},
                            "channelHandle": {"runs": [{"text": "@dummyhandle"}]},
                            "accountPhoto": {"thumbnails": [
                              {"url": "https://example.com/photo-small.jpg", "width": 40, "height": 40},
                              {"url": "https://example.com/photo-large.jpg", "width": 88, "height": 88}
                            ]}
                          }
                        }
                      }
                    }
                  }
                }
              ]
            }
        """

        private const val ACCOUNT_MENU_NO_ACCOUNT = """{"actions": []}"""

        private const val ACCOUNT_MENU_NO_NAME = """
            {
              "actions": [
                {
                  "openPopupAction": {
                    "popup": {
                      "multiPageMenuRenderer": {
                        "header": {
                          "activeAccountHeaderRenderer": {
                            "accountName": {"runs": []},
                            "email": {"runs": [{"text": "dummy@example.com"}]},
                            "accountPhoto": {"thumbnails": []}
                          }
                        }
                      }
                    }
                  }
                }
              ]
            }
        """

        private const val ACCOUNTS_LIST_SELECTED = """
            {
              "contents": [
                {"accountItemRenderer": {
                  "accountName": {"simpleText": "Akun Nonaktif"},
                  "isDisabled": true, "hasChannel": true, "isSelected": false,
                  "serviceEndpoint": {"datasyncId": "delegatedDisabled||userDisabled"}
                }},
                {"accountItemRenderer": {
                  "accountName": {"simpleText": "Akun Kedua"},
                  "hasChannel": true, "isSelected": false,
                  "serviceEndpoint": {"dataSyncId": "delegatedA||userA"}
                }},
                {"accountItemRenderer": {
                  "accountName": {"simpleText": "Akun Utama"},
                  "hasChannel": true, "isSelected": true,
                  "serviceEndpoint": {"datasyncId": "delegatedB||userB"}
                }}
              ]
            }
        """

        private const val ACCOUNTS_LIST_MAIN = """
            {
              "responseContext": {
                "mainAppWebResponseContext": {"dataSyncId": "delegatedMain||userMain"}
              },
              "contents": [
                {"accountItemRenderer": {
                  "accountName": {"simpleText": "Akun Kedua"},
                  "hasChannel": true, "isSelected": false,
                  "serviceEndpoint": {"dataSyncId": "delegatedA||userA"}
                }}
              ]
            }
        """

        private const val ACCOUNTS_LIST_NONE_SELECTED = """
            {
              "contents": [
                {"accountItemRenderer": {
                  "accountName": {"simpleText": "Akun Pertama"},
                  "hasChannel": true, "isSelected": false,
                  "serviceEndpoint": {"dataSyncId": "delegatedA||userA"}
                }},
                {"accountItemRenderer": {
                  "accountName": {"simpleText": "Akun Kedua"},
                  "hasChannel": true, "isSelected": false,
                  "serviceEndpoint": {"datasyncId": "delegatedB||userB"}
                }}
              ]
            }
        """

        private const val ACCOUNTS_LIST_EMPTY = """{"contents": []}"""
    }
}
