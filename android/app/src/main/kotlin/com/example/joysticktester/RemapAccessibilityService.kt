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
            Log.d(TAG, "gamepad key: code=${event.keyCode} action=${event.action} repeat=${event.repeatCount}")
        }
        return false
    }

    companion object {
        private const val TAG = "RemapA11y"

        @Volatile
        var filterKeyEventsAvailable: Boolean = false

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

/** 判斷 KeyEvent 是否來自遊戲搖桿來源（Stage 1 用 keyCode 白名單比較保守）。 */
fun isGamepadEvent(event: KeyEvent): Boolean {
    if (event.deviceId == KeyEvent.DEVICE_ID_UNKNOWN) {
        return false
    }
    val code = event.keyCode
    return code in AccessibleGamepadKeys
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