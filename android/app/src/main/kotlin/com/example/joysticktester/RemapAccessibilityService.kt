package com.example.joysticktester

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Stage 1：搖桿映射器骨架。
 * 僅全域攔截搖桿 KeyEvent 並記錄，尚不進行任何觸控注入。
 * onKeyEvent 一律回傳 false（放行），確保不影響任何現有行為。
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
        logInputDevices()
    }

    /**
     * 暫時診斷：列出全部輸入裝置及其來源，並標示是否具備 D-pad（HAT）軸。
     * 目的：判定 SR-001 的 D-pad 是走 HAT 軸（MotionEvent）還是按鍵（KeyEvent）。
     */
    private fun logInputDevices() {
        try {
            for (device in InputDevice.getDevices()) {
                val sources = device.sources
                val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) != 0
                val isJoystick = (sources and InputDevice.SOURCE_JOYSTICK) != 0
                Log.i(
                    TAG,
                    "device name=${device.name} id=${device.id} " +
                        "sources=${Integer.toHexString(sources)} " +
                        "gamepad=$isGamepad joystick=$isJoystick",
                )
                for (axis in AXES_TO_PROBE) {
                    val range = try {
                        device.getMotionRange(axis)
                    } catch (_: Exception) {
                        null
                    } ?: continue
                    Log.i(
                        TAG,
                        "  axis=${axisLabel(axis)} min=${range.min} max=${range.max} " +
                            "flat=${range.flat} " +
                            "source=${Integer.toHexString(range.source)}",
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "logInputDevices failed: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Stage 1 不處理 UI 事件。
    }

    override fun onInterrupt() {
        // 系統要求中斷。
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        // 暫時診斷：入口全量 Log，不受白名單限制。
        // 目的：確認 Android 是否真的把任何 KeyEvent 送進 onKeyEvent()。
        val entryDesc = if (event == null) {
            "null"
        } else {
            "keyCode=${event.keyCode} action=${actionLabel(event.action)} " +
                "repeatCount=${event.repeatCount} deviceId=${event.deviceId} " +
                "source=${Integer.toHexString(event.source)}"
        }
        Log.i(TAG, "onKeyEvent ENTRY $entryDesc")
        if (event == null) {
            return false
        }
        if (isGamepadEvent(event)) {
            val desc = "keyCode=${event.keyCode} " +
                "action=${actionLabel(event.action)} " +
                "deviceId=${event.deviceId} " +
                "repeatCount=${event.repeatCount}"
            Log.i(TAG, "onKeyEvent $desc")
            recordKeyEvent(desc)
        }
        return false
    }

    companion object {
        private const val TAG = "RemapA11y"
        private const val MAX_HISTORY = 40

        @Volatile
        var filterKeyEventsAvailable: Boolean = false

        /** 最近一筆符合白名單的 KeyEvent 描述（供診斷 UI 顯示，不影響行為）。 */
        @Volatile
        var lastWhitelistedEvent: String? = null

        private val historyLock = Any()
        private val keyEventHistory = ArrayDeque<String>()

        /** 記錄一筆符合白名單的 KeyEvent 描述到最近事件歷史。 */
        fun recordKeyEvent(desc: String) {
            synchronized(historyLock) {
                keyEventHistory.addLast(desc)
                while (keyEventHistory.size > MAX_HISTORY) {
                    keyEventHistory.removeFirst()
                }
            }
            lastWhitelistedEvent = desc
        }

        /** 回傳最近的事件歷史（新→舊依序的過往序列）。 */
        fun getKeyEventHistory(): List<String> = synchronized(historyLock) {
            keyEventHistory.toList()
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

/** 判斷 KeyEvent 是否為遊戲搖桿按鍵（Stage 1 採用 keyCode 白名單）。 */
fun isGamepadEvent(event: KeyEvent): Boolean {
    return event.keyCode in AccessibleGamepadKeys
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

/** D-pad 通常以 HAT 軸（AXIS_HAT_X/HAT_Y）呈現，先用這兩個判定是否為軸式 D-pad。 */
private val AXES_TO_PROBE = intArrayOf(
    MotionEvent.AXIS_HAT_X,
    MotionEvent.AXIS_HAT_Y,
)

private fun axisLabel(axis: Int): String = when (axis) {
    MotionEvent.AXIS_HAT_X -> "HAT_X"
    MotionEvent.AXIS_HAT_Y -> "HAT_Y"
    else -> "axis($axis)"
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