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

    /**
     * 方向區（大圈）設定。cx/cy/r 都是 0~1 比例。
     * 四個方向鍵（UP/DOWN/LEFT/RIGHT）不各自存座標真值，
     * 一律由這個圈的 cx/cy/r 依 [DPAD_INSET] 推導。
     */
    data class VirtualDpad(
        var cx: Double,
        var cy: Double,
        var r: Double,
        var visible: Boolean,
        var opacity: Double,
    )

    /** 方向鍵（D-pad）的按鈕 id。 */
    const val DIR_UP = "UP"
    const val DIR_DOWN = "DOWN"
    const val DIR_LEFT = "LEFT"
    const val DIR_RIGHT = "RIGHT"

    /** 方向 marker 內縮係數：marker 落在 r*k 的位置，不貼圓周。 */
    const val DPAD_INSET = 0.9

    private const val KEY_DPAD = "virtualDpad"

    private val DirectionIds = setOf(DIR_UP, DIR_DOWN, DIR_LEFT, DIR_RIGHT)
    private val DirectionKeyCodes = mapOf(
        DIR_UP to KeyEventKeyCodes.DPAD_UP,
        DIR_DOWN to KeyEventKeyCodes.DPAD_DOWN,
        DIR_LEFT to KeyEventKeyCodes.DPAD_LEFT,
        DIR_RIGHT to KeyEventKeyCodes.DPAD_RIGHT,
    )

    /** 方向鍵的預設 keyCode（固定，UI 不可改）。 */
    private object KeyEventKeyCodes {
        const val DPAD_UP = 19
        const val DPAD_DOWN = 20
        const val DPAD_LEFT = 21
        const val DPAD_RIGHT = 22
    }

    fun defaultButtonIds(): List<String> =
        listOf(
            "A", "B", "X", "Y", "L1", "R1", "L2", "R2",
            DIR_UP, DIR_DOWN, DIR_LEFT, DIR_RIGHT,
        )

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
        val dpad = parseDpad(root)
        val out = LinkedHashMap<String, VirtualButton>()
        for (id in defaultButtonIds()) {
            val d = defaults(id)
            val j = obj.optJSONObject(id)
            val base = if (j == null) {
                // 舊版 config 沒有此 id（如升級後才新增的 L1/R1/L2/R2）：補預設值，
                // 不需使用者先按儲存。
                d
            } else {
                VirtualButton(
                    id = id,
                    physicalKeyCode = j.optInt("physicalKeyCode", d.physicalKeyCode),
                    xRatio = j.optDouble("xRatio", d.xRatio),
                    yRatio = j.optDouble("yRatio", d.yRatio),
                    sizeRatio = j.optDouble("sizeRatio", d.sizeRatio),
                    visible = j.optBoolean("visible", d.visible),
                    opacity = j.optDouble("opacity", d.opacity),
                )
            }
            out[id] = if (id in DirectionIds) {
                // 方向鍵位置/顯示/透明度一律由方向圈推導，
                // 不採用各自儲存的座標/visible/opacity 真值。
                val p = dpadPosition(id, dpad)
                base.copy(
                    xRatio = p.first,
                    yRatio = p.second,
                    visible = dpad.visible,
                    opacity = dpad.opacity,
                )
            } else {
                base
            }
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
        val dpad = parseDpad(root)
        root.put(KEY_DPAD, writeDpad(dpad))
        // 方向鍵位置/顯示/透明度一律由方向圈推導，覆寫各自儲存的真值。
        for (id in DirectionIds) {
            val p = dpadPosition(id, dpad)
            val j = vb.getJSONObject(id)
            j.put("xRatio", p.first)
            j.put("yRatio", p.second)
            j.put("visible", dpad.visible)
            j.put("opacity", dpad.opacity)
        }
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
        return JSONObject()
            .put("virtualButtons", vb)
            .put(KEY_DPAD, writeDpad(defaultDpad()))
            .toString()
    }

    private fun defaults(id: String): VirtualButton = when (id) {
        "A" -> VirtualButton("A", 190, 0.86, 0.60, 0.11, true, 0.6)
        "B" -> VirtualButton("B", 189, 0.95, 0.42, 0.11, true, 0.6)
        "X" -> VirtualButton("X", 191, 0.77, 0.42, 0.11, true, 0.6)
        "Y" -> VirtualButton("Y", 188, 0.86, 0.24, 0.11, true, 0.6)
        "L1" -> VirtualButton("L1", 192, 0.18, 0.16, 0.10, true, 0.6)
        "R1" -> VirtualButton("R1", 193, 0.82, 0.16, 0.10, true, 0.6)
        "L2" -> VirtualButton("L2", 194, 0.18, 0.28, 0.10, true, 0.6)
        "R2" -> VirtualButton("R2", 195, 0.82, 0.28, 0.10, true, 0.6)
        DIR_UP, DIR_DOWN, DIR_LEFT, DIR_RIGHT -> {
            val d = defaultDpad()
            val p = dpadPosition(id, d)
            VirtualButton(
                id = id,
                physicalKeyCode = DirectionKeyCodes.getValue(id),
                xRatio = p.first,
                yRatio = p.second,
                sizeRatio = if (DIR_UP == id || DIR_DOWN == id) 0.095 else 0.09,
                visible = d.visible,
                opacity = d.opacity,
            )
        }
        else -> VirtualButton(id, 0, 0.5, 0.5, 0.1, true, 0.6)
    }

    /** 預設方向圈：左側偏下、半徑視覺比例 0.18。 */
    fun defaultDpad(): VirtualDpad = VirtualDpad(cx = 0.30, cy = 0.55, r = 0.18, visible = true, opacity = 0.4)

    /** 由方向圈推導單一方向鍵的 (xRatio, yRatio)，並 clamp 到 0~1。 */
    fun dpadPosition(id: String, d: VirtualDpad): Pair<Double, Double> {
        val inset = d.r * DPAD_INSET
        val x = when (id) {
            DIR_LEFT -> d.cx - inset
            DIR_RIGHT -> d.cx + inset
            else -> d.cx
        }
        val y = when (id) {
            DIR_UP -> d.cy - inset
            DIR_DOWN -> d.cy + inset
            else -> d.cy
        }
        return clamp01(x) to clamp01(y)
    }

    /** 從 JSON 讀方向圈；缺欄位或缺整個 virtualDpad 用預設。 */
    fun parseDpad(root: JSONObject): VirtualDpad {
        val d = root.optJSONObject(KEY_DPAD)
        val def = defaultDpad()
        val dpad = VirtualDpad(
            cx = d?.optDouble("cx", def.cx) ?: def.cx,
            cy = d?.optDouble("cy", def.cy) ?: def.cy,
            r = d?.optDouble("r", def.r) ?: def.r,
            visible = d?.optBoolean("visible", def.visible) ?: def.visible,
            opacity = d?.optDouble("opacity", def.opacity) ?: def.opacity,
        )
        dpad.cx = clamp01(dpad.cx)
        dpad.cy = clamp01(dpad.cy)
        dpad.r = clamp01(dpad.r)
        return dpad
    }

    private fun writeDpad(d: VirtualDpad): JSONObject =
        JSONObject()
            .put("cx", d.cx)
            .put("cy", d.cy)
            .put("r", d.r)
            .put("visible", d.visible)
            .put("opacity", d.opacity)

    private fun clamp01(v: Double): Double = v.coerceIn(0.0, 1.0)
}