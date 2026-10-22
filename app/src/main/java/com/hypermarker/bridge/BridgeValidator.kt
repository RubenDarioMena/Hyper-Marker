package com.hypermarker.bridge

import org.json.JSONArray
import org.json.JSONObject

class BridgeValidator(private val session: String, private val documentId: String, private val revision: Int) {
    private val seen = LinkedHashSet<String>()
    private var windowStart = 0L
    private var count = 0
    fun validate(raw: String?, mode: String, now: Long = System.currentTimeMillis()): JSONObject? = runCatching {
        require(raw != null && raw.length <= 512_000)
        if (now - windowStart > 1000) { windowStart = now; count = 0 }
        require(++count <= 90)
        val message = JSONObject(raw)
        require(message.getInt("protocolVersion") == 1 && message.getString("sessionId") == session)
        require(message.getString("documentId") == documentId && message.getInt("revision") == revision)
        val request = message.getString("requestId")
        require(request.length <= 100 && seen.add(request))
        if (seen.size > 1000) seen.remove(seen.first())
        val payload = message.getJSONObject("payload")
        require(payload.toString().length <= 500_000)
        when (message.getString("type")) {
            "controller.ready", "anchor.unresolved", "annotation.activated", "tap", "hold" -> Unit
            "longpress" -> {
                require(mode == "VIEW" || mode == "DRAW")
                listOf("x", "y").forEach { key -> require(payload.getDouble(key).isFinite()) }
                payload.optJSONObject("target")?.let { target ->
                    require(mode == "VIEW" && target.getString("tag").length <= 40)
                    val rect = target.getJSONObject("rect")
                    listOf("x", "y", "width", "height").forEach { key -> require(rect.getDouble(key).isFinite() && rect.getDouble(key) in -10_000_000.0..10_000_000.0) }
                    require(rect.getDouble("width") > 0 && rect.getDouble("height") > 0)
                }
            }
            "target.selected" -> {
                require(mode == "EDIT")
                val anchor = payload.getJSONObject("anchor")
                require(anchor.getString("strategy") in listOf("DOM", "SURFACE", "PAGE"))
                require(anchor.optString("selector").length <= 4096)
                listOf("relativeX", "relativeY").forEach { key -> require(anchor.getDouble(key).isFinite() && anchor.getDouble(key) in 0.0..1.0) }
                require(anchor.getString("tag").length <= 40 && anchor.optString("text").length <= 160)
                payload.optJSONObject("text")?.let { text -> listOf("text", "original", "raw").forEach { key -> require(text.optString(key).length <= 20_000) } }
                listOf("pins", "texts").forEach { key -> payload.optJSONArray(key)?.let { ids -> require(ids.length() <= 10_000 && ids.strings().all { it.length <= 100 }) } }
            }
            "stroke.completed" -> {
                require(mode == "DRAW")
                val points = payload.getJSONArray("points")
                require(points.length() in 1..20_000)
                for (i in 0 until points.length()) {
                    val point = points.getJSONArray(i)
                    require(point.length() == 2)
                    for (j in 0..1) require(point.getDouble(j).isFinite() && point.getDouble(j) in 0.0..10_000_000.0)
                }
                require(payload.getString("color").matches(Regex("#[a-fA-F0-9]{6}")))
                require(payload.getDouble("width").isFinite() && payload.getDouble("width") in 1.0..50.0)
                require(payload.getString("tool") in listOf("pen", "highlighter"))
            }
            "stroke.erase" -> require(mode == "DRAW")
            else -> error("Evento no permitido")
        }
        message
    }.getOrNull()
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
