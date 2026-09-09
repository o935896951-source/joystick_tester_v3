package com.example.joysticktester

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
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
        Log.i(TAG, "onServiceConnected filterKeyEvents=$canFilterKeys")
        filterKeyEventsAvailable = canFilterKeys
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Stage 1 不處理 UI 事件。
    }

    override fun onInterrupt() {
        // 系統要求中斷。
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) {
            return false
        }
        if (isGamepadEvent(event)) {
            val desc = "keyCode=${event.keyCode} " +
                "action=${actionLabel(event.action)} " +
                "deviceId=${event.deviceId} " +
                "repeatCount=${event.repeatCount}"
            Log.i(TAG, "onKeyEvent $desc")
            lastWhitelistedEvent = desc
        }
        return false
    }

    companion object {
        private const val TAG = "RemapA11y"

        @Volatile
        var filterKeyEventsAvailable: Boolean = false

        /** 最近一筆符合白名單的 KeyEvent 描述（供診斷 UI 顯示，不影響行為）。 */
        @Volatile
        var lastWhitelistedEvent: String? = null

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