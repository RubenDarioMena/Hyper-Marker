package com.hypermarker

import com.hypermarker.bridge.BridgeValidator
import org.json.JSONArray
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
    private fun press(request: String, target: JSONObject? = null) = message("longpress", request = request)
        .put("payload", JSONObject().put("x", 10).put("y", 20).put("target", target ?: JSONObject.NULL))
    @Test fun acceptsOnlyCurrentModeSessionAndUniqueRequests() {
        val validator = BridgeValidator("s", "d", 1)
        assertNull(validator.validate(message().toString(), "VIEW"))
        assertNull(validator.validate(message(session = "old", request = "r2").toString(), "EDIT"))
        val current = message(request = "r3").toString()
        assertNotNull(validator.validate(current, "EDIT"))
        assertNull(validator.validate(current, "EDIT"))
        assertNull(validator.validate(message(type = "filesystem.read", request = "r4").toString(), "EDIT"))
        assertNull(validator.validate("x".repeat(600_000), "EDIT"))
    }
    @Test fun rejectsOutOfRangeGeometryAndOtherDocuments() {
        val validator = BridgeValidator("s", "d", 1)
        val invalid = message()
        invalid.getJSONObject("payload").getJSONObject("anchor").put("relativeX", 2)
        assertNull(validator.validate(invalid.toString(), "EDIT"))
        assertNull(validator.validate(message(request = "r5").put("documentId", "other").toString(), "EDIT"))
    }
    @Test fun longPressAndSelectionPayloadsAreBounded() {
        val validator = BridgeValidator("s", "d", 1)
        val rect = JSONObject().put("x", 0).put("y", 40).put("width", 300).put("height", 80)
        assertNotNull(validator.validate(press("p1", JSONObject().put("tag", "p").put("rect", rect)).toString(), "VIEW"))
        assertNotNull(validator.validate(press("p2").toString(), "DRAW"))
        assertNull(validator.validate(press("p3", JSONObject().put("tag", "p").put("rect", rect)).toString(), "DRAW"))
        assertNull(validator.validate(press("p4").toString(), "EDIT"))
        assertNull(validator.validate(press("p5", JSONObject().put("tag", "p").put("rect", JSONObject(rect.toString()).put("width", 0))).toString(), "VIEW"))
        assertNotNull(validator.validate(message("tap", request = "t1").toString(), "EDIT"))
        val huge = message(request = "s1")
        huge.getJSONObject("payload").put("text", JSONObject().put("text", "x".repeat(20_001)))
        assertNull(validator.validate(huge.toString(), "EDIT"))
        val ids = message(request = "s2")
        ids.getJSONObject("payload").put("pins", JSONArray().put("a".repeat(101)))
        assertNull(validator.validate(ids.toString(), "EDIT"))
    }
}
