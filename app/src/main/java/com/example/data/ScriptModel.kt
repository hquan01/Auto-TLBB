package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Category of Auto Scripts
 */
enum class ScriptCategory(val displayName: String, val iconEmoji: String) {
    FARMING("Trồng Trọt & Nông Trại", "🌾"),
    FISHING("Auto Câu Cá", "🎣"),
    COMBAT("Đánh Quái & Cày Cấp", "⚔️"),
    MINING("Đào Khoáng & Chế Đồ", "⛏️"),
    GENERAL("Auto Chạm Siêu Tốc", "⚡");

    companion object {
        fun fromString(value: String): ScriptCategory {
            return try {
                valueOf(value)
            } catch (_: Exception) {
                GENERAL
            }
        }
    }
}

/**
 * Action Type for individual script point
 */
enum class ScriptActionType {
    CLICK,
    SWIPE
}

/**
 * Represents an individual target point inside a Script
 */
data class ScriptPoint(
    val id: Int,
    var name: String = "Điểm $id",
    var x: Float = 300f,
    var y: Float = 600f,
    var endX: Float = 300f,           // For swipe gestures
    var endY: Float = 400f,           // For swipe gestures
    var actionType: ScriptActionType = ScriptActionType.CLICK,
    var delayBeforeMs: Long = 0L,     // Thời gian chờ trước khi bấm (ms)
    var clickDurationMs: Long = 40L,   // Thời gian giữ/chạm (ms)
    var delayAfterMs: Long = 100L      // Thời gian chờ sau khi bấm (ms)
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("x", x.toDouble())
            put("y", y.toDouble())
            put("endX", endX.toDouble())
            put("endY", endY.toDouble())
            put("actionType", actionType.name)
            put("delayBeforeMs", delayBeforeMs)
            put("clickDurationMs", clickDurationMs)
            put("delayAfterMs", delayAfterMs)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ScriptPoint {
            return ScriptPoint(
                id = json.optInt("id", 1),
                name = json.optString("name", "Điểm ${json.optInt("id", 1)}"),
                x = json.optDouble("x", 300.0).toFloat(),
                y = json.optDouble("y", 600.0).toFloat(),
                endX = json.optDouble("endX", 300.0).toFloat(),
                endY = json.optDouble("endY", 400.0).toFloat(),
                actionType = try {
                    ScriptActionType.valueOf(json.optString("actionType", "CLICK"))
                } catch (_: Exception) {
                    ScriptActionType.CLICK
                },
                delayBeforeMs = json.optLong("delayBeforeMs", 0L),
                clickDurationMs = json.optLong("clickDurationMs", 40L),
                delayAfterMs = json.optLong("delayAfterMs", 100L)
            )
        }
    }
}

/**
 * Represents a complete Auto Click Script
 */
data class AutoClickScript(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Kịch bản mới",
    var category: ScriptCategory = ScriptCategory.FARMING,
    var repeatCount: Int = 0,         // 0 = Vô hạn, > 0 = số vòng lặp
    var loopDelayMs: Long = 200L,     // Thời gian nghỉ giữa các vòng lặp (ms)
    var stopTimerSeconds: Long = 0L,  // 0 = Chạy liên tục, > 0 = Tự dừng sau X giây
    var points: List<ScriptPoint> = listOf(ScriptPoint(1, "Điểm 1", 300f, 600f, delayBeforeMs = 0L, clickDurationMs = 40L, delayAfterMs = 100L)),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        val pointsArray = JSONArray()
        points.forEach { pointsArray.put(it.toJson()) }

        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("category", category.name)
            put("repeatCount", repeatCount)
            put("loopDelayMs", loopDelayMs)
            put("stopTimerSeconds", stopTimerSeconds)
            put("points", pointsArray)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): AutoClickScript {
            val pointsList = mutableListOf<ScriptPoint>()
            val pointsArray = json.optJSONArray("points")
            if (pointsArray != null) {
                for (i in 0 until pointsArray.length()) {
                    pointsList.add(ScriptPoint.fromJson(pointsArray.getJSONObject(i)))
                }
            }
            if (pointsList.isEmpty()) {
                pointsList.add(ScriptPoint(1, "Điểm 1", 300f, 600f, delayBeforeMs = 0L, clickDurationMs = 40L, delayAfterMs = 100L))
            }

            return AutoClickScript(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "Kịch bản Auto"),
                category = ScriptCategory.fromString(json.optString("category", "FARMING")),
                repeatCount = json.optInt("repeatCount", 0),
                loopDelayMs = json.optLong("loopDelayMs", 200L),
                stopTimerSeconds = json.optLong("stopTimerSeconds", 0L),
                points = pointsList,
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
            )
        }

        fun getDefaultPresets(): List<AutoClickScript> {
            return listOf(
                // 1. FARMING (Trồng trọt & Nông trại)
                AutoClickScript(
                    id = "preset_farm_plant_harvest",
                    name = "🌾 Auto Trồng Trọt: Gieo Hạt & Thu Hoạch",
                    category = ScriptCategory.FARMING,
                    repeatCount = 50,
                    loopDelayMs = 2000L,
                    stopTimerSeconds = 1800L, // 30 phút
                    points = listOf(
                        ScriptPoint(1, "1. Chọn Túi Hạt Giống", 250f, 750f, delayBeforeMs = 0L, clickDurationMs = 40L, delayAfterMs = 250L),
                        ScriptPoint(2, "2. Gieo Ô Đất Số 1", 400f, 650f, delayBeforeMs = 50L, clickDurationMs = 40L, delayAfterMs = 200L),
                        ScriptPoint(3, "3. Gieo Ô Đất Số 2", 550f, 650f, delayBeforeMs = 50L, clickDurationMs = 40L, delayAfterMs = 200L),
                        ScriptPoint(4, "4. Thu Hoạch / Liềm Cắt", 400f, 850f, delayBeforeMs = 100L, clickDurationMs = 50L, delayAfterMs = 300L)
                    )
                ),
                AutoClickScript(
                    id = "preset_farm_water_fertilizer",
                    name = "🌾 Auto Nông Trại: Tưới Nước & Bón Phân",
                    category = ScriptCategory.FARMING,
                    repeatCount = 0,
                    loopDelayMs = 3000L,
                    stopTimerSeconds = 900L, // 15 phút
                    points = listOf(
                        ScriptPoint(1, "Bình Tưới Nước", 200f, 750f, delayBeforeMs = 0L, clickDurationMs = 40L, delayAfterMs = 300L),
                        ScriptPoint(2, "Tưới Vườn Đất", 450f, 650f, delayBeforeMs = 100L, clickDurationMs = 60L, delayAfterMs = 500L),
                        ScriptPoint(3, "Túi Bón Phân", 700f, 750f, delayBeforeMs = 150L, clickDurationMs = 40L, delayAfterMs = 300L)
                    )
                ),

                // 2. FISHING (Câu cá)
                AutoClickScript(
                    id = "preset_fishing_pro",
                    name = "🎣 Auto Câu Cá: Thả Mồi & Giật Cần",
                    category = ScriptCategory.FISHING,
                    repeatCount = 100,
                    loopDelayMs = 4000L,
                    stopTimerSeconds = 3600L, // 1 giờ
                    points = listOf(
                        ScriptPoint(1, "1. Ném Cần Câu / Thả Mồi", 500f, 850f, delayBeforeMs = 0L, clickDurationMs = 50L, delayAfterMs = 500L),
                        ScriptPoint(2, "2. Nhấp Giữ Phao (Giằng cá)", 500f, 850f, delayBeforeMs = 3500L, clickDurationMs = 120L, delayAfterMs = 150L),
                        ScriptPoint(3, "3. Nhấp Kéo Dây Lên Bờ", 500f, 850f, delayBeforeMs = 100L, clickDurationMs = 40L, delayAfterMs = 150L)
                    )
                ),

                // 3. COMBAT (Đánh quái & Cày cấp)
                AutoClickScript(
                    id = "preset_combat_rpg_combo",
                    name = "⚔️ Auto Đánh Quái: Combo Skill & Bơm Máu",
                    category = ScriptCategory.COMBAT,
                    repeatCount = 0,
                    loopDelayMs = 800L,
                    stopTimerSeconds = 0L,
                    points = listOf(
                        ScriptPoint(1, "Đánh Thường (Spam)", 800f, 850f, delayBeforeMs = 0L, clickDurationMs = 30L, delayAfterMs = 100L),
                        ScriptPoint(2, "Kỹ Năng 1 (AOE)", 700f, 750f, delayBeforeMs = 50L, clickDurationMs = 40L, delayAfterMs = 250L),
                        ScriptPoint(3, "Kỹ Năng 2 (Stun / Dồn dame)", 600f, 850f, delayBeforeMs = 80L, clickDurationMs = 40L, delayAfterMs = 300L),
                        ScriptPoint(4, "Bình Máu / Mana HP định kỳ", 200f, 400f, delayBeforeMs = 100L, clickDurationMs = 40L, delayAfterMs = 200L)
                    )
                ),

                // 4. MINING (Đào khoáng & Chế đồ)
                AutoClickScript(
                    id = "preset_mining_craft",
                    name = "⛏️ Auto Khai Thác: Đập Đá & Đổi Cuốc",
                    category = ScriptCategory.MINING,
                    repeatCount = 0,
                    loopDelayMs = 1200L,
                    stopTimerSeconds = 1800L,
                    points = listOf(
                        ScriptPoint(1, "Đập Quặng Khoáng (Giữ)", 450f, 650f, delayBeforeMs = 0L, clickDurationMs = 200L, delayAfterMs = 400L),
                        ScriptPoint(2, "Thu Lượm Vàng / Kim Cương", 450f, 650f, delayBeforeMs = 100L, clickDurationMs = 40L, delayAfterMs = 200L),
                        ScriptPoint(3, "Đổi Cuốc / Phục Hồi Độ Bền", 150f, 850f, delayBeforeMs = 100L, clickDurationMs = 40L, delayAfterMs = 300L)
                    )
                ),

                // 5. GENERAL (Click siêu tốc)
                AutoClickScript(
                    id = "preset_single_fast",
                    name = "⚡ Auto Clicker Siêu Tốc (1 Điểm Phá Giáp)",
                    category = ScriptCategory.GENERAL,
                    repeatCount = 0,
                    loopDelayMs = 30L,
                    stopTimerSeconds = 0L,
                    points = listOf(
                        ScriptPoint(1, "Điểm Siêu Tốc", 450f, 750f, delayBeforeMs = 0L, clickDurationMs = 20L, delayAfterMs = 25L)
                    )
                )
            )
        }
    }
}
