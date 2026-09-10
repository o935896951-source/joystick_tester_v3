package com.example.joysticktester

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.InputDevice
import android.view.accessibility.AccessibilityEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stage 1：搖桿映射器骨架。
 * 僅全域攔截搖桿 KeyEvent 並記錄，尚不進行任何觸控注入。
 * onKeyEvent 一律回傳 false（放行），確保不影響任何現有行為。
 *
 * 【Stage 1 診斷階段】onKeyEvent 維持「只 Log + return false」完全不變。
 * 此檔案額外提供 thread-safe 的 raw 診斷歷史 recorder，
 * 供 MainActivity 把每個 KeyEvent（PASS / DROP / 非 gamepad）寫入。
 */
class RemapAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        val flags = serviceInfo?.flags ?: 0
        val canFilterKeys =
            (flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0
        Log.i(
            TAG,
            "onServiceConnected filterKeyEvents=$canFilterKeys " +
                "flags=${Integer.toHexString(flags)}",
        )
        filterKeyEventsAvailable = canFilterKeys
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Stage 1 不處理 UI 事件。
    }

    override fun onInterrupt() {
        // 系統要求中斷。
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event != null) {
            val devDesc = event.device?.let { dev ->
                val srcs = dev.sources
                " deviceId=${event.deviceId} " +
                    "deviceName=${dev.name} " +
                    "deviceSources=${Integer.toHexString(srcs)} " +
                    "isGamepadSource=${(srcs and InputDevice.SOURCE_GAMEPAD) != 0} " +
                    "isJoystickSource=${(srcs and InputDevice.SOURCE_JOYSTICK) != 0}"
            } ?: " noDevice"
            // 暫時診斷：入口全量 Log，不受白名單限制。
            // 目的：確認 Android 是否真的把任何 KeyEvent 送進 onKeyEvent()。
            Log.i(
                TAG,
                "onKeyEvent ENTRY keyCode=${event.keyCode} " +
                    "action=${actionLabel(event.action)} " +
                    "repeatCount=${event.repeatCount} " +
                    "source=${Integer.toHexString(event.source)}" +
                    devDesc,
            )
            if (isGamepadEvent(event)) {
                val desc = "keyCode=${event.keyCode} " +
                    "action=${actionLabel(event.action)} " +
                    "deviceId=${event.deviceId} " +
                    "repeatCount=${event.repeatCount}"
                Log.i(TAG, "onKeyEvent $desc")
            }

            // 診斷（純記錄、不改變任何行為）：
            // 把 a11y 收到的每一個 KeyEvent 原樣記入共享 raw 歷史。
            // - 不跑 Gate（避免動到 pressedKeyCodes 狀態機，影響 MainActivity 的去重/emit）
            // - 不 emit、不 injection、不攔截、不轉發（仍一律 return false 放行）
            // - 與 origin="activity" 各自獨立保留，供判斷事件到底有沒有走到 MainActivity。
            recordRawEvent(
                GamepadEventRecord(
                    timestamp = nowTimestamp(),
                    origin = "a11y",
                    keyCode = event.keyCode,
                    logicalKey = logicalKeyLabel(event.keyCode),
                    action = actionLabel(event.action),
                    deviceId = event.deviceId,
                    repeatCount = event.repeatCount,
                    source = event.source,
                    downTime = event.downTime,
                    eventTime = event.eventTime,
                    gate = "A11Y_PASSTHRU",
                    emitted = false,
                ),
            )
        }
        return false
    }

    companion object {
        private const val TAG = "RemapA11y"
        private const val MAX_HISTORY = 40

        @Volatile
        var filterKeyEventsAvailable: Boolean = false

        /** 最近一筆 raw 診斷紀錄描述（供診斷 UI 顯示，不影響行為）。 */
        @Volatile
        var lastRecordedEvent: String? = null

        private val historyLock = Any()
        private val keyEventHistory = ArrayDeque<GamepadEventRecord>()

        /**
         * 記錄一筆 raw 事件（PASS / DROP / NOT_EVALUATED / A11Y_PASSTHRU 皆記錄），
         * 維持最多 [MAX_HISTORY] 筆（最舊的先被移除）。
         */
        fun recordRawEvent(record: GamepadEventRecord) {
            val count: Int
            synchronized(historyLock) {
                keyEventHistory.addLast(record)
                while (keyEventHistory.size > MAX_HISTORY) {
                    keyEventHistory.removeFirst()
                }
                count = keyEventHistory.size
            }
            lastRecordedEvent = record.toCopyString()
            Log.i(
                TAG,
                "[GP-DIAG-HISTORY] record origin=${record.origin} " +
                    "keyCode=${record.keyCode} action=${record.action} " +
                    "gate=${record.gate} emitted=${record.emitted} count=$count",
            )
        }

        /** 回傳最近的 raw 事件歷史（舊→新的原始 arrival 順序，每筆為一行可複製格式）。 */
        fun getKeyEventHistory(): List<String> = synchronized(historyLock) {
            val list = keyEventHistory.map { it.toCopyString() }
            Log.i(
                TAG,
                "[GP-DIAG-HISTORY] getHistory count=${list.size} " +
                    "latest=${list.lastOrNull() ?: "none"}",
            )
            list
        }

        /** 清空診斷歷史（只清 deque，不影響任何按鍵設定 / SharedPreferences）。 */
        fun clearKeyEventHistory() {
            synchronized(historyLock) {
                keyEventHistory.clear()
            }
            lastRecordedEvent = null
            Log.i(TAG, "[GP-DIAG-HISTORY] cleared")
        }

        /** 診斷統計：RAW / PASS / DROP / UNKNOWN 與收件來源（activity / a11y）計數。 */
        fun getKeyEventHistoryStats(): Map<String, Int> = synchronized(historyLock) {
            var pass = 0
            var drop = 0
            var unknown = 0
            var activity = 0
            var a11y = 0
            for (r in keyEventHistory) {
                when {
                    r.gate == "PASS" -> pass++
                    r.gate.startsWith("DROP") -> drop++
                }
                if (r.logicalKey.startsWith("UNKNOWN")) unknown++
                when (r.origin) {
                    "activity" -> activity++
                    "a11y" -> a11y++
                }
            }
            mapOf(
                "raw" to keyEventHistory.size,
                "pass" to pass,
                "drop" to drop,
                "unknown" to unknown,
                "activity" to activity,
                "a11y" to a11y,
            )
        }

        /** 是否已啟用此無障礙服務（唯讀檢查，供權限流程 UI 使用）。 */
        fun isServiceEnabled(context: Context): Boolean {
            val expected = ComponentName(context, RemapAccessibilityService::class.java)
                .flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}

/**
 * 一筆 raw 診斷事件紀錄。
 *
 * @param timestamp  "yyyy-MM-dd HH:mm:ss.SSS"
 * @param origin     來源路徑： "activity"（MainActivity.dispatchKeyEvent）/
 *                   "a11y"（RemapAccessibilityService.onKeyEvent）
 * @param keyCode    原始 keyCode（96 與 190 各自保留，不 merge）
 * @param logicalKey 顯示用邏輯名稱（目前僅標準鍵有名字，alias 顯示 UNKNOWN(190)）
 * @param action     DOWN / UP / MULTIPLE / UNKNOWN(...)
 * @param deviceId   裝置 id
 * @param repeatCount 長按重複次數
 * @param source     InputDevice source 原始 int（顯示時輸出 hex(dec)）
 * @param downTime   KeyEvent.downTime
 * @param eventTime  KeyEvent.eventTime
 * @param gate       PASS / DROP_REPEAT / DROP_ALREADY_DOWN /
 *                   DROP_NO_MATCHING_DOWN / DROP_IGNORED / NOT_EVALUATED /
 *                   A11Y_PASSTHRU（a11y 收件，未經 Gate 判定）
 * @param emitted    是否已 emit 到 Flutter EventChannel
 */
data class GamepadEventRecord(
    val timestamp: String,
    val origin: String,
    val keyCode: Int,
    val logicalKey: String,
    val action: String,
    val deviceId: Int,
    val repeatCount: Int,
    val source: Int,
    val downTime: Long,
    val eventTime: Long,
    val gate: String,
    val emitted: Boolean,
) {
    /** source 的十六進位（含十進位）顯示，例如 "0x401(1025)"。 */
    fun sourceLabel(): String = "0x${Integer.toHexString(source)}($source)"

    /** 多行顯示格式（LastRecordedEvent 等內部使用）。 */
    fun toDisplayString(): String = buildString {
        appendLine(timestamp)
        appendLine(origin)
        appendLine("keyCode=$keyCode")
        appendLine("logical=$logicalKey")
        appendLine(action)
        appendLine("deviceId=$deviceId")
        appendLine("repeat=$repeatCount")
        appendLine("source=${sourceLabel()}")
        appendLine("downTime=$downTime")
        appendLine("eventTime=$eventTime")
        appendLine("gate=$gate")
        append("emitted=$emitted")
    }

    /** 一筆一行純文字，供「複製全部」/「單筆複製」/ MethodChannel 傳輸。 */
    fun toCopyString(): String =
        "$timestamp | origin=$origin | keyCode=$keyCode | logical=$logicalKey | " +
            "action=$action | deviceId=$deviceId | repeat=$repeatCount | " +
            "source=${sourceLabel()} | downTime=$downTime | eventTime=$eventTime | " +
            "gate=$gate | emitted=$emitted"
}

/** 目前時間，格式 「yyyy-MM-dd HH:mm:ss.SSS」。 */
fun nowTimestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

/**
 * 顯示用邏輯鍵名稱。
 * 注意：此處【不】把 SR-001 的 alias（190/189/191/188）對映成 A/B/X/Y；
 * 那段是為了「先確認 A 是否真的同時產生標準與 alias 兩種 keyCode」，
 * 因此 alias 一律顯示 UNKNOWN(190)。
 */
fun logicalKeyLabel(keyCode: Int): String = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_A -> "A"
    KeyEvent.KEYCODE_BUTTON_B -> "B"
    KeyEvent.KEYCODE_BUTTON_X -> "X"
    KeyEvent.KEYCODE_BUTTON_Y -> "Y"
    KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
    KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
    KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
    KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
    KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
    KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
    KeyEvent.KEYCODE_BUTTON_START -> "START"
    KeyEvent.KEYCODE_BUTTON_SELECT -> "SELECT"
    KeyEvent.KEYCODE_BUTTON_MODE -> "MODE"
    KeyEvent.KEYCODE_DPAD_UP -> "DPAD_UP"
    KeyEvent.KEYCODE_DPAD_DOWN -> "DPAD_DOWN"
    KeyEvent.KEYCODE_DPAD_LEFT -> "DPAD_LEFT"
    KeyEvent.KEYCODE_DPAD_RIGHT -> "DPAD_RIGHT"
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "PLAY_PAUSE"
    KeyEvent.KEYCODE_SPACE -> "SPACE"
    KeyEvent.KEYCODE_DPAD_CENTER -> "ENTER"
    else -> "UNKNOWN($keyCode)"
}

/** 判斷 KeyEvent 是否為遊戲搖桿按鍵（Stage 1 採用 keyCode 白名單）。 */
fun isGamepadEvent(event: KeyEvent): Boolean {
    return event.keyCode in AccessibleGamepadKeys || event.keyCode in RemapAliasKeyCodes
}

/** 把 KeyEvent 的 action 轉成可讀字串，供診斷顯示。 */
fun actionLabel(action: Int): String {
    return when (action) {
        KeyEvent.ACTION_DOWN -> "DOWN"
        KeyEvent.ACTION_UP -> "UP"
        KeyEvent.ACTION_MULTIPLE -> "MULTIPLE"
        else -> "UNKNOWN($action)"
    }
}

private val AccessibleGamepadKeys = setOf(
    KeyEvent.KEYCODE_DPAD_UP,        // 19
    KeyEvent.KEYCODE_DPAD_DOWN,      // 20
    KeyEvent.KEYCODE_DPAD_LEFT,      // 21
    KeyEvent.KEYCODE_DPAD_RIGHT,     // 22
    KeyEvent.KEYCODE_BUTTON_A,       // 96
    KeyEvent.KEYCODE_BUTTON_B,       // 97
    KeyEvent.KEYCODE_BUTTON_X,       // 99
    KeyEvent.KEYCODE_BUTTON_Y,       // 100
    KeyEvent.KEYCODE_BUTTON_L1,      // 102
    KeyEvent.KEYCODE_BUTTON_R1,      // 103
    KeyEvent.KEYCODE_BUTTON_L2,      // 104
    KeyEvent.KEYCODE_BUTTON_R2,      // 105
    KeyEvent.KEYCODE_BUTTON_THUMBL,  // 106
    KeyEvent.KEYCODE_BUTTON_THUMBR,  // 107
    KeyEvent.KEYCODE_BUTTON_START,   // 108
    KeyEvent.KEYCODE_BUTTON_SELECT,  // 109
    KeyEvent.KEYCODE_BUTTON_MODE,    // 110
)

/** SR-001 實際發送的非標準面板按鍵 keycode：A=190 B=189 X=191 Y=188。 */
private val RemapAliasKeyCodes = setOf(
    190, // A（SR-001）
    189, // B（SR-001）
    191, // X（SR-001）
    188, // Y（SR-001）
)