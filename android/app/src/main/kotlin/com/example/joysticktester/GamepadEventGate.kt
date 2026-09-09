package com.example.joysticktester

import android.view.KeyEvent

/**
 * 搖桿按鍵的「單一實體事件」閘門。
 *
 * 目的：同一個物理按壓只產生一次 DOWN、一次 UP，並消除韌體/驅動層的
 * 重複報告（例如 2×DOWN + 2×UP、長按的 repeat DOWN、重複放開）。
 * 純狀態機（edge-trigger），不使用任何時間型 debounce。
 *
 * 共用於 MainActivity（前景收件）與 RemapAccessibilityService（a11y 收件），
 * 兩者皆在 app 同一個 process，因此同一個物理事件只會被記錄一次。
 */
object GamepadEventGate {
    private val pressedKeyCodes = mutableSetOf<Int>()

    /**
     * 判定這個 KeyEvent 是否為「一次物理操作」的代表性事件。
     *
     * @return "down"（真按下）、"up"（真放開），或 null（重複/幽靈/長按重複，應丟棄）
     */
    fun ingestKeyEvent(event: KeyEvent): String? {
        return synchronized(this) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount > 0) return@synchronized null
                    if (pressedKeyCodes.add(event.keyCode)) "down" else null
                }
                KeyEvent.ACTION_UP -> {
                    if (pressedKeyCodes.remove(event.keyCode)) "up" else null
                }
                else -> null
            }
        }
    }

    /** 重新同步：清除所有「按下」狀態，避免漏收 DOWN 造成幽靈 UP。 */
    fun reset() {
        synchronized(this) {
            pressedKeyCodes.clear()
        }
    }
}