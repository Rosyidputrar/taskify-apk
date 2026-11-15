package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.common.Hashes
import com.rosyidputrar.taskify.core.domain.sync.CrudOp
import com.rosyidputrar.taskify.core.domain.sync.CrudOpType.DELETE
import com.rosyidputrar.taskify.core.domain.sync.CrudOpType.PATCH
import com.rosyidputrar.taskify.core.domain.sync.CrudOpType.PUT
import com.rosyidputrar.taskify.core.domain.sync.isVaultRotation
import com.rosyidputrar.taskify.core.domain.sync.rotationPayload
import com.rosyidputrar.taskify.core.domain.sync.rotationResultError
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultRotationTest {
    private val keyPatch = CrudOp(PATCH, "pw_keys", "k1", buildJsonObject { put("wrapped_key", JsonPrimitive("w2")); put("rotated_from", JsonPrimitive("abc")) })
    private val itemPatch = CrudOp(PATCH, "pw_items", "i1", buildJsonObject { put("enc", JsonPrimitive("new")); put("v", JsonPrimitive(1)) }, mapOf("enc" to "old"))

    @Test fun detectsRotationOnlyWhenWrappedKeyChanges() {
        assertTrue(isVaultRotation(listOf(keyPatch, itemPatch)))
        assertFalse(isVaultRotation(listOf(itemPatch)))
        assertFalse(isVaultRotation(listOf(CrudOp(PATCH, "pw_keys", "k1", buildJsonObject { put("iterations", JsonPrimitive(1)) }))))
    }

    @Test fun payloadCarriesFingerprintOfPreviousEnc() {
        val payload = rotationPayload(listOf(keyPatch, itemPatch))!!
        assertEquals("k1", payload["p_key"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("w2", payload["p_key"]!!.jsonObject["wrapped_key"]!!.jsonPrimitive.content)
        val row = payload["p_rows"]!!.jsonArray.single().jsonObject
        assertEquals("pw_items", row["t"]!!.jsonPrimitive.content)
        assertEquals("new", row["enc"]!!.jsonPrimitive.content)
        assertEquals(Hashes.sha256Hex16("old"), row["b"]!!.jsonPrimitive.content)
    }

    @Test fun missingPreviousValueSendsNullSoServerRejects() {
        val row = rotationPayload(listOf(keyPatch, itemPatch.copy(previousValues = null)))!!["p_rows"]!!.jsonArray.single().jsonObject
        assertEquals(JsonNull, row["b"])
    }

    @Test fun invalidShapesAreRejectedWhole() {
        assertNull(rotationPayload(listOf(keyPatch, keyPatch)))
        assertNull(rotationPayload(listOf(keyPatch, itemPatch.copy(op = PUT))))
        assertNull(rotationPayload(listOf(keyPatch, CrudOp(PATCH, "tasks", "t", null))))
        assertNull(rotationPayload(listOf(keyPatch.copy(op = DELETE))))
    }

    @Test fun rpcResultCodeIsAnError() {
        assertEquals("23V01", rotationResultError(buildJsonObject { put("code", JsonPrimitive("23V01")) })?.code)
        assertNull(rotationResultError(buildJsonObject { put("ok", JsonPrimitive(true)) }))
        assertNull(rotationResultError(null))
    }
}
