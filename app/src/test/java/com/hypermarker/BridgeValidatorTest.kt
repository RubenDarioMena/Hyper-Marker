package com.hypermarker

import com.hypermarker.bridge.BridgeValidator
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BridgeValidatorTest {
    private fun message(type: String = "target.selected", session: String = "s", request: String = "r") = JSONObject()
        .put("protocolVersion", 1).put("sessionId", session).put("documentId", "d").put("revision", 1).put("requestId", request).put("type", type)
        .put("payload", JSONObject().put("anchor", JSONObject().put("strategy", "DOM").put("selector", "#a").put("tag", "p").put("text", "Texto").put("relativeX", .5).put("relativeY", .5)))
    @Test fun acceptsOnlyCurrentModeSessionAndUniqueRequests() {
        val validator = BridgeValidator("s", "d", 1)
        assertNull(validator.validate(message().toString(), "VIEW"))
        assertNull(validator.validate(message(session = "old", request = "r2").toString(), "PIN"))
        val current = message(request = "r3").toString()
        assertNotNull(validator.validate(current, "PIN"))
        assertNull(validator.validate(current, "PIN"))
        assertNull(validator.validate(message(type = "filesystem.read", request = "r4").toString(), "PIN"))
        assertNull(validator.validate("x".repeat(600_000), "PIN"))
    }
    @Test fun rejectsOutOfRangeGeometryAndOtherDocuments() {
        val validator = BridgeValidator("s", "d", 1)
        val invalid = message()
        invalid.getJSONObject("payload").getJSONObject("anchor").put("relativeX", 2)
        assertNull(validator.validate(invalid.toString(), "PIN"))
        assertNull(validator.validate(message(request = "r5").put("documentId", "other").toString(), "PIN"))
    }
}
