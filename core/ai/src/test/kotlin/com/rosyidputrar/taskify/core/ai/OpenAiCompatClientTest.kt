package com.rosyidputrar.taskify.core.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenAiCompatClientTest {
    private val requests = mutableListOf<Pair<HttpRequestData, String>>()

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): OpenAiCompatClient =
        OpenAiCompatClient("https://api.example.com/v1", "sk-rahasia", HttpClient(MockEngine { req ->
            requests += req to String(req.body.toByteArray())
            handler(req)
        }))

    private fun MockRequestHandleScope.sse(vararg lines: String) =
        respond(lines.joinToString("") { "data: $it\n\n" }, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))

    @Test fun modelsAreListedSortedWithBearerKey() = runBlocking {
        val c = client { respond("""{"object":"list","data":[{"id":"gpt-4o"},{"id":"gpt-4.1-mini"},{"id":"gpt-4o"}]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        assertEquals(listOf("gpt-4.1-mini", "gpt-4o"), c.models())
        val (req, _) = requests.single()
        assertEquals("https://api.example.com/v1/models", req.url.toString())
        assertEquals("Bearer sk-rahasia", req.headers[HttpHeaders.Authorization])
    }

    @Test fun errorsAreMappedWithServerMessage() = runBlocking {
        val c = client { respond("""{"error":{"message":"Incorrect API key provided: sk-r****","type":"invalid_request_error"}}""", HttpStatusCode.Unauthorized) }
        val e = assertFailsWith<AiException> { c.models() }
        assertEquals(AiErrorKind.Auth, e.kind)
        assertEquals(401, e.status)
        assertTrue(e.message!!.startsWith("API key ditolak (401)."))
        assertTrue(e.message!!.contains("Incorrect API key"))
        // Bukan JSON (mis. base URL salah → halaman HTML).
        val html = client { respond("<html>Not Found</html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html")) }
        assertEquals(AiErrorKind.BadResponse, assertFailsWith<AiException> { html.models() }.kind)
        val down = client { throw IOException("Connection reset") }
        assertEquals(AiErrorKind.Network, assertFailsWith<AiException> { down.models() }.kind)
    }

    @Test fun streamsTextAndAssemblesToolCallsFromChunks() = runBlocking {
        val c = client {
            sse(
                """{"choices":[{"index":0,"delta":{"role":"assistant","content":"Saya "},"finish_reason":null}]}""",
                """{"choices":[{"index":0,"delta":{"content":"cek dulu."}}]}""",
                // Dua alat, potongan argumen berselang-seling; id hanya di potongan pertama.
                """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_a","type":"function","function":{"name":"cari","arguments":""}}]}}]}""",
                """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"id":"call_b","type":"function","function":{"name":"tugas","arguments":"{\"rentang\":"}}]}}]}""",
                """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"kata\":\"lis"}}]}}]}""",
                """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"trik\"}"}},{"index":1,"function":{"arguments":"\"minggu\"}"}}]}}]}""",
                """{"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""",
                "[DONE]",
            )
        }
        val events = c.stream(AiRequest("gpt-4o", "sys", listOf(AiMessage(AiRole.User, "hai")))).toList()
        assertEquals(
            listOf(
                AiEvent.Text("Saya "), AiEvent.Text("cek dulu."),
                AiEvent.ToolCalls(listOf(AiToolCall("call_a", "cari", "{\"kata\":\"listrik\"}"), AiToolCall("call_b", "tugas", "{\"rentang\":\"minggu\"}"))),
                AiEvent.Done("tool_calls"),
            ),
            events,
        )
    }

    @Test fun midStreamErrorIsRaised() = runBlocking {
        val c = client { sse("""{"choices":[{"delta":{"content":"Hal"}}]}""", """{"error":{"message":"Model overloaded"}}""") }
        val e = assertFailsWith<AiException> { c.stream(AiRequest("m", null, listOf(AiMessage(AiRole.User, "x")))).toList() }
        assertTrue(e.message!!.contains("Model overloaded"))
    }

    @Test fun serverThatIgnoresStreamingStillWorks() = runBlocking {
        val c = client {
            respond(
                """{"choices":[{"message":{"role":"assistant","content":"Halo!","tool_calls":[{"id":"c1","type":"function","function":{"name":"cari","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"),
            )
        }
        val events = c.stream(AiRequest("m", null, listOf(AiMessage(AiRole.User, "x")))).toList()
        assertEquals(listOf(AiEvent.Text("Halo!"), AiEvent.ToolCalls(listOf(AiToolCall("c1", "cari", "{}"))), AiEvent.Done("tool_calls")), events)
    }

    @Test fun jsonErrorBodyIsRaisedLikeSseError() = runBlocking {
        val c = client {
            respond(
                """{"error":{"message":"Model sedang sibuk","type":"server_error"}}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val e = assertFailsWith<AiException> { c.stream(AiRequest("m", null, listOf(AiMessage(AiRole.User, "x")))).toList() }
        assertEquals(AiErrorKind.Server, e.kind)
        assertTrue(e.message!!.contains("Model sedang sibuk"))
    }

    @Test fun requestBodyCarriesMaxCompletionTokens() = runBlocking {
        val c = client { sse("""{"choices":[{"delta":{"content":"ok"},"finish_reason":"stop"}]}""", "[DONE]") }
        c.stream(AiRequest("gpt-4o", null, listOf(AiMessage(AiRole.User, "x")), maxTokens = 1234)).toList()
        val body = AiJson.parseToJsonElement(requests.single().second).jsonObject
        assertEquals("1234", body["max_completion_tokens"]!!.jsonPrimitive.content)
        // Bawaan tetap 4096 bila tidak diisi.
        requests.clear()
        c.stream(AiRequest("gpt-4o", null, listOf(AiMessage(AiRole.User, "x")))).toList()
        val body2 = AiJson.parseToJsonElement(requests.single().second).jsonObject
        assertEquals("4096", body2["max_completion_tokens"]!!.jsonPrimitive.content)
    }

    @Test fun requestBodyCarriesSystemHistoryToolResultsAndTools() = runBlocking {
        val c = client { sse("""{"choices":[{"delta":{"content":"ok"},"finish_reason":"stop"}]}""", "[DONE]") }
        val tool = AiToolSpec("cari", "Cari catatan", buildJsonObject { put("type", "object") })
        c.stream(
            AiRequest(
                "gpt-4o", "Kamu asisten.",
                listOf(
                    AiMessage(AiRole.User, "cari listrik"),
                    AiMessage(AiRole.Assistant, "", toolCalls = listOf(AiToolCall("call_a", "cari", ""))),
                    AiMessage(AiRole.Tool, "2 hasil", toolCallId = "call_a", toolName = "cari"),
                ),
                tools = listOf(tool),
            ),
        ).toList()
        val (req, raw) = requests.single()
        assertEquals("https://api.example.com/v1/chat/completions", req.url.toString())
        val body = AiJson.parseToJsonElement(raw).jsonObject
        assertEquals("gpt-4o", body["model"]!!.jsonPrimitive.content)
        assertEquals("true", body["stream"]!!.jsonPrimitive.content)
        val msgs = body["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user", "assistant", "tool"), msgs.map { it["role"]!!.jsonPrimitive.content })
        val call = msgs[2]["tool_calls"]!!.jsonArray.single().jsonObject
        assertEquals("{}", call["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content) // argumen kosong → "{}"
        assertEquals("call_a", msgs[3]["tool_call_id"]!!.jsonPrimitive.content)
        assertEquals("cari", (body["tools"] as JsonArray).single().jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test fun noToolsKeyWhenThereAreNoTools() = runBlocking {
        val c = client { sse("""{"choices":[{"delta":{"content":"ok"}}]}""") }
        c.stream(AiRequest("m", null, listOf(AiMessage(AiRole.User, "x")))).toList()
        val body = AiJson.parseToJsonElement(requests.single().second) as JsonObject
        assertFalse("tools" in body)
        assertEquals("user", body["messages"]!!.jsonArray.single().jsonObject["role"]!!.jsonPrimitive.content) // tanpa system kosong
    }
}
