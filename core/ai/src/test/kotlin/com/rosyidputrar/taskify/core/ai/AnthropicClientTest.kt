package com.rosyidputrar.taskify.core.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnthropicClientTest {
    private val requests = mutableListOf<Pair<HttpRequestData, String>>()

    private fun client(body: String, status: HttpStatusCode = HttpStatusCode.OK, type: String = "text/event-stream") =
        AnthropicClient("https://api.anthropic.com", "sk-ant", HttpClient(MockEngine { req ->
            requests += req to String(req.body.toByteArray())
            respond(body, status, headersOf(HttpHeaders.ContentType, type))
        }))

    private fun sse(vararg events: Pair<String, String>) = events.joinToString("") { (e, d) -> "event: $e\ndata: $d\n\n" }

    @Test fun streamsTextAndToolUseWithPartialJson() = runBlocking {
        val c = client(
            sse(
                "message_start" to """{"type":"message_start","message":{"id":"msg_1","role":"assistant","content":[]}}""",
                "content_block_start" to """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
                "ping" to """{"type":"ping"}""",
                "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Saya buatkan "}}""",
                "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"tugasnya."}}""",
                "content_block_stop" to """{"type":"content_block_stop","index":0}""",
                "content_block_start" to """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_1","name":"buat_tugas","input":{}}}""",
                "content_block_delta" to """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"judul\": \"Bayar"}}""",
                "content_block_delta" to """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":" listrik\"}"}}""",
                "content_block_stop" to """{"type":"content_block_stop","index":1}""",
                "message_delta" to """{"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":20}}""",
                "message_stop" to """{"type":"message_stop"}""",
            ),
        )
        val events = c.stream(AiRequest("claude-x", "sys", listOf(AiMessage(AiRole.User, "buat tugas")))).toList()
        assertEquals(
            listOf(
                AiEvent.Text("Saya buatkan "), AiEvent.Text("tugasnya."),
                AiEvent.ToolCalls(listOf(AiToolCall("toolu_1", "buat_tugas", "{\"judul\": \"Bayar listrik\"}"))),
                AiEvent.Done("tool_use"),
            ),
            events,
        )
        val (req, _) = requests.single()
        assertEquals("https://api.anthropic.com/v1/messages", req.url.toString())
        assertEquals("sk-ant", req.headers["x-api-key"])
        assertEquals("2023-06-01", req.headers["anthropic-version"])
    }

    @Test fun overloadedErrorEventIsRateLimit() = runBlocking {
        val c = client(sse("error" to """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""))
        val e = assertFailsWith<AiException> { c.stream(AiRequest("m", null, listOf(AiMessage(AiRole.User, "x")))).toList() }
        assertEquals(AiErrorKind.RateLimit, e.kind)
    }

    @Test fun httpErrorUsesServerMessage() = runBlocking {
        val c = client("""{"type":"error","error":{"type":"not_found_error","message":"model: claude-x"}}""", HttpStatusCode.NotFound, "application/json")
        val e = assertFailsWith<AiException> { c.stream(AiRequest("claude-x", null, listOf(AiMessage(AiRole.User, "x")))).toList() }
        assertEquals(AiErrorKind.NotFound, e.kind)
        assertEquals(true, e.message!!.contains("model: claude-x"))
    }

    @Test fun bodyMergesRolesAndSendsToolResultsAsUserBlocks() = runBlocking {
        val c = client(sse("message_stop" to """{"type":"message_stop"}"""))
        c.stream(
            AiRequest(
                "claude-x", "Kamu asisten.",
                listOf(
                    AiMessage(AiRole.Assistant, "sapaan lama"), // diawali assistant → dibuang
                    AiMessage(AiRole.User, "cari dan buat"),
                    AiMessage(AiRole.Assistant, "", toolCalls = listOf(AiToolCall("t1", "cari", "{\"kata\":\"x\"}"), AiToolCall("t2", "tugas", "rusak{"))),
                    AiMessage(AiRole.Tool, "hasil 1", toolCallId = "t1"),
                    AiMessage(AiRole.Tool, "", toolCallId = "t2"),
                    AiMessage(AiRole.User, "lanjut"),
                ),
                tools = listOf(AiToolSpec("cari", "Cari", buildJsonObject { put("type", "object") })),
                maxTokens = 1234,
            ),
        ).toList()
        val body = AiJson.parseToJsonElement(requests.single().second).jsonObject
        assertEquals("Kamu asisten.", body["system"]!!.jsonPrimitive.content)
        assertEquals("1234", body["max_tokens"]!!.jsonPrimitive.content)
        assertEquals("object", body["tools"]!!.jsonArray.single().jsonObject["input_schema"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val msgs = body["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("user", "assistant", "user"), msgs.map { it["role"]!!.jsonPrimitive.content })
        val assistant = msgs[1]["content"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("tool_use", "tool_use"), assistant.map { it["type"]!!.jsonPrimitive.content }) // teks kosong dibuang
        assertEquals("x", assistant[0]["input"]!!.jsonObject["kata"]!!.jsonPrimitive.content)
        assertEquals(0, assistant[1]["input"]!!.jsonObject.size) // argumen rusak → {}
        val results = msgs[2]["content"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("tool_result", "tool_result", "text"), results.map { it["type"]!!.jsonPrimitive.content })
        assertEquals("(kosong)", results[1]["content"]!!.jsonPrimitive.content)
    }

    @Test fun modelsList() = runBlocking {
        val c = client("""{"data":[{"id":"claude-sonnet-x","type":"model"},{"id":"claude-haiku-x","type":"model"}],"has_more":false}""", type = "application/json")
        assertEquals(listOf("claude-haiku-x", "claude-sonnet-x"), c.models())
        assertEquals("https://api.anthropic.com/v1/models?limit=1000", requests.single().first.url.toString())
    }
}
