package org.valudus.core

import kotlinx.serialization.json.*

object GambitSiameseCapablanca {
    fun evaluate(input: JsonObject): JsonObject = when (input["operation"]!!.jsonPrimitive.content) {
        "board_allowed" -> buildJsonObject { put("allowed", boardAllowed(input)) }
        "advance_pair" -> advancePair(input)
        else -> error("unsupported operation")
    }

    private fun boardAllowed(input: JsonObject): Boolean {
        val board = input["board"]!!.jsonPrimitive.content
        val rotation = input["rotation"]!!.jsonObject
        val over = rotation["over"]!!.jsonObject
        if (over[board]!!.jsonPrimitive.boolean) return false
        val other = if (board == "a") "b" else "a"
        return when (rotation["turn_style"]!!.jsonPrimitive.content) {
            "independent" -> true
            "alternating" -> over[other]!!.jsonPrimitive.boolean || (rotation["history_length"]!!.jsonPrimitive.int % 2 == 0) == (board == "a")
            "simultaneous" -> rotation["turn"]!!.jsonObject[board]!!.jsonPrimitive.content == rotation["pair_phase"]!!.jsonPrimitive.content && !rotation["moved_this_pair"]!!.jsonObject[board]!!.jsonPrimitive.boolean
            else -> error("unsupported turn style")
        }
    }

    private fun advancePair(input: JsonObject): JsonObject {
        val board = input["board"]!!.jsonPrimitive.content
        val moved = input["moved_this_pair"]!!.jsonObject
        val over = input["over"]!!.jsonObject
        val movedA = (if (board == "a") true else moved["a"]!!.jsonPrimitive.boolean)
        val movedB = (if (board == "b") true else moved["b"]!!.jsonPrimitive.boolean)
        val complete = (movedA || over["a"]!!.jsonPrimitive.boolean) && (movedB || over["b"]!!.jsonPrimitive.boolean)
        return buildJsonObject {
            putJsonObject("moved_this_pair") { put("a", if (complete) false else movedA); put("b", if (complete) false else movedB) }
            put("pair_phase", if (complete) if (input["moved_color"]!!.jsonPrimitive.content == "w") "b" else "w" else input["pair_phase"]!!.jsonPrimitive.content)
        }
    }
}
