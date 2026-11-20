package com.rosyidputrar.taskify.core.innertube

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fasad `MyTube.selectedAccount()` dan perilaku baru `accountInfo()`:
 * gagal parse → [UnrecognizedAccountResponseException] yang membawa
 * cuplikan mentah yang sudah di-scrub. Fixture JSON minimal inline.
 */
class SelectedAccountTest {

    private fun myTubeWith(menuJson: String, channelsJson: String): MyTube {
        val engine = MockEngine { request ->
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
        return MyTube(
            InnerTube(
                HttpClient(engine) {
                    expectSuccess = true
                    defaultRequest { url("https://music.youtube.com/youtubei/v1/") }
                },
            ),
        )
    }

    @Test
    fun `selectedAccount mengembalikan akun terpilih dan dataSyncId`() = runTest {
        val myTube = myTubeWith(ACCOUNT_MENU_OK, ACCOUNTS_LIST_SELECTED)
        val result = myTube.selectedAccount()
        assertTrue(result.isSuccess)
        val selected = result.getOrThrow()
        assertEquals("Akun Utama", selected.info.name)
        assertEquals("utama@example.com", selected.info.email)
        assertEquals("delegatedB||userB", selected.dataSyncId)
    }

    @Test
    fun `selectedAccount gagal bila tanpa akun terpilih`() = runTest {
        val myTube = myTubeWith(ACCOUNT_MENU_OK, ACCOUNTS_LIST_NONE_SELECTED)
        val result = myTube.selectedAccount()
        assertTrue(result.isFailure)
        val cause = result.exceptionOrNull()
        assertTrue(cause is IllegalStateException)
        assertEquals("akun terpilih tidak ditemukan", cause.message)
    }

    @Test
    fun `accountInfo tanpa akun melempar exception pembawa cuplikan mentah`() = runTest {
        val myTube = myTubeWith(ACCOUNT_MENU_NO_ACCOUNT, ACCOUNTS_LIST_EMPTY)
        val result = myTube.accountInfo()
        assertTrue(result.isFailure)
        val cause = result.exceptionOrNull()
        assertTrue(cause is UnrecognizedAccountResponseException, cause.toString())
        assertTrue(cause.message?.contains("info akun tidak ditemukan") == true, cause.message)
        // Cuplikan = badan mentah (tanpa akun, tapi tetap terlihat).
        assertTrue(cause.rawSnippet.contains("responseContext"), cause.rawSnippet)
        assertFalse(cause.rawSnippet.contains("SAPISID"), cause.rawSnippet)
    }

    @Test
    fun `accountInfo badan bukan JSON melempar exception dengan cuplikan scrub`() = runTest {
        val html = "<html><body>Error SAPISID=rahasia-123; Authorization: Bearer token-rahasia</body></html>"
        val myTube = myTubeWith(html, ACCOUNTS_LIST_EMPTY)
        val result = myTube.accountInfo()
        assertTrue(result.isFailure)
        val cause = result.exceptionOrNull()
        assertTrue(cause is UnrecognizedAccountResponseException, cause.toString())
        assertFalse(cause.rawSnippet.contains("rahasia-123"), cause.rawSnippet)
        assertFalse(cause.rawSnippet.contains("token-rahasia"), cause.rawSnippet)
        assertFalse(cause.rawSnippet.contains("SAPISID"), cause.rawSnippet)
        assertFalse(cause.rawSnippet.contains("Authorization"), cause.rawSnippet)
        assertTrue(cause.rawSnippet.contains("<html>"), cause.rawSnippet)
    }

    @Test
    fun `accountInfo badan kosong memberi cuplikan respons kosong status 200`() = runTest {
        val myTube = myTubeWith("", ACCOUNTS_LIST_EMPTY)
        val result = myTube.accountInfo()
        assertTrue(result.isFailure)
        val cause = result.exceptionOrNull()
        assertTrue(cause is UnrecognizedAccountResponseException, cause.toString())
        assertEquals("respons kosong, status 200", cause.rawSnippet)
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

        private const val ACCOUNT_MENU_NO_ACCOUNT = """{"responseContext": {}, "actions": []}"""

        private const val ACCOUNTS_LIST_SELECTED = """
            {
              "contents": [
                {"accountItemRenderer": {
                  "accountName": {"runs": [{"text": "Akun Lain"}]},
                  "isSelected": false,
                  "serviceEndpoint": {"dataSyncId": "delegatedA||userA"}
                }},
                {"accountItemRenderer": {
                  "accountName": {"runs": [{"text": "Akun Utama"}]},
                  "accountByline": {"runs": [{"text": "utama@example.com"}]},
                  "isSelected": true,
                  "serviceEndpoint": {"datasyncId": "delegatedB||userB"}
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
                }}
              ]
            }
        """

        private const val ACCOUNTS_LIST_EMPTY = """{"contents": []}"""
    }
}
