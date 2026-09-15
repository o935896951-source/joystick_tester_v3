package com.example.joysticktester

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * 虛擬按鈕映射設定存放於原生 SharedPreferences("remap_prefs")。
 * - Flutter 透過 MethodChannel 讀寫（同一份 JSON 字串）。
 * - RemapAccessibilityService 背景直接用 loadMap() 讀同一份。
 * 兩個 Context 都指向同一個 app process，因此資料一致。
 */
object RemapConfigStore {
    private const val TAG = "RemapConfig"
    private const val PREFS = "remap_prefs"
    private const val KEY = "remap_config"

    /** 一顆可設定的虛擬觸控按鈕。座標全部用 0~1 比例。 */
    data class VirtualButton(
        val id: String,
        var physicalKeyCode: Int,
        var xRatio: Double,
        var yRatio: Double,
        var sizeRatio: Double,
        var visible: Boolean,
        var opacity: Double,
    )

    fun defaultButtonIds(): List<String> = listOf("A", "B", "X", "Y")

    /** 回傳目前儲存的 JSON；從未儲存時回傳預設。 */
    fun toJsonString(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY, null) ?: defaultJson()
    }

    /** 儲存（會先 sanitize 成固定結構，缺欄位用預設）。 */
    fun saveJson(context: Context, json: String) {
        val normalized = try {
            normalize(json)
        } catch (e: Exception) {
            Log.e(TAG, "saveJson invalid json, using default", e)
            defaultJson()
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, normalized).apply()
    }

    /** 讀成 VirtualButton 地圖（id -> button），供 a11y 背景使用。 */
    fun loadMap(context: Context): Map<String, VirtualButton> {
        val root = try {
            JSONObject(toJsonString(context))
        } catch (e: Exception) {
            Log.e(TAG, "loadMap parse error, using default", e)
            JSONObject(defaultJson())
        }
        val obj = try {
            root.getJSONObject("virtualButtons")
        } catch (e: Exception) {
            Log.e(TAG, "loadMap missing virtualButtons", e)
            null
        } ?: return LinkedHashMap()
        val out = LinkedHashMap<String, VirtualButton>()
        for (id in defaultButtonIds()) {
            val j = obj.optJSONObject(id) ?: continue
            val d = defaults(id)
            out[id] = VirtualButton(
                id = id,
                physicalKeyCode = j.optInt("physicalKeyCode", d.physicalKeyCode),
                xRatio = j.optDouble("xRatio", d.xRatio),
                yRatio = j.optDouble("yRatio", d.yRatio),
                sizeRatio = j.optDouble("sizeRatio", d.sizeRatio),
                visible = j.optBoolean("visible", d.visible),
                opacity = j.optDouble("opacity", d.opacity),
            )
        }
        return out
    }

    private fun normalize(json: String): String {
        val root = JSONObject(json)
        val vb = root.optJSONObject("virtualButtons") ?: JSONObject()
        for (id in defaultButtonIds()) {
            val d = defaults(id)
            val j = vb.optJSONObject(id) ?: JSONObject()
            val cleaned = JSONObject()
            cleaned.put("physicalKeyCode", j.optInt("physicalKeyCode", d.physicalKeyCode))
            cleaned.put("xRatio", j.optDouble("xRatio", d.xRatio))
            cleaned.put("yRatio", j.optDouble("yRatio", d.yRatio))
            cleaned.put("sizeRatio", j.optDouble("sizeRatio", d.sizeRatio))
            cleaned.put("visible", j.optBoolean("visible", d.visible))
            cleaned.put("opacity", j.optDouble("opacity", d.opacity))
            vb.put(id, cleaned)
        }
        root.put("virtualButtons", vb)
        return root.toString()
    }

    private fun defaultJson(): String {
        val vb = JSONObject()
        for (id in defaultButtonIds()) {
            val d = defaults(id)
            val j = JSONObject()
            j.put("physicalKeyCode", d.physicalKeyCode)
            j.put("xRatio", d.xRatio)
            j.put("yRatio", d.yRatio)
            j.put("sizeRatio", d.sizeRatio)
            j.put("visible", d.visible)
            j.put("opacity", d.opacity)
            vb.put(id, j)
        }
        return JSONObject().put("virtualButtons", vb).toString()
    }

    private fun defaults(id: String): VirtualButton = when (id) {
        "A" -> VirtualButton("A", 190, 0.86, 0.60, 0.11, true, 0.6)
        "B" -> VirtualButton("B", 189, 0.95, 0.42, 0.11, true, 0.6)
        "X" -> VirtualButton("X", 191, 0.77, 0.42, 0.11, true, 0.6)
        "Y" -> VirtualButton("Y", 188, 0.86, 0.24, 0.11, true, 0.6)
        else -> VirtualButton(id, 0, 0.5, 0.5, 0.1, true, 0.6)
    }
}