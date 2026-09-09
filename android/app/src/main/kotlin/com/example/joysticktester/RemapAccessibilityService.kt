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
                recordKeyEvent(desc)
            }
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