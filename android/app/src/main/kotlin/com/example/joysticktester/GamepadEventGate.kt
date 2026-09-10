package com.example.joysticktester

import android.util.Log
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
 *
 * 【Stage 1 診斷階段】去重邏輯完全不變（repeatCount>0 / alreadyDown /
 * 無對應 DOWN 的 UP），只多回傳「決策原因」，供 MainActivity 寫入 raw
 * 診斷歷史。不 merge 96/190、不改 pressedKeyCodes、不接 a11y。
 */
object GamepadEventGate {
    private const val TAG = "GamepadEvtGate"
    private val pressedKeyCodes = mutableSetOf<Int>()

    /**
     * gate 判定結果。
     *
     * @param decision        PASS / DROP_REPEAT / DROP_ALREADY_DOWN /
     *                        DROP_NO_MATCHING_DOWN / DROP_IGNORED
     * @param canonicalAction PASS 時為 "down"/"up"（與既有 emit 語意一致），
     *                        其餘為 null
     */
    data class GateOutcome(
        val decision: GateDecision,
        val canonicalAction: String?,
    )

    /**
     * 判定這個 KeyEvent 是否為「一次物理操作」的代表性事件。
     *
     * @return GateOutcome：
     *   PASS（canonicalAction = "down"/"up"）→ 真按下/真放開；
     *   DROP_*（canonicalAction = null）→ 重複/幽靈/長按重複，應丟棄。
     */
    fun ingestKeyEvent(event: KeyEvent): GateOutcome {
        return synchronized(this) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount > 0) {
                        Log.i(TAG, "GATE DROP repeatDown keyCode=${event.keyCode} repeat=${event.repeatCount}")
                        return@synchronized GateOutcome(GateDecision.DROP_REPEAT, null)
                    }
                    if (pressedKeyCodes.add(event.keyCode)) {
                        Log.i(TAG, "GATE PASS down keyCode=${event.keyCode}")
                        GateOutcome(GateDecision.PASS_DOWN, "down")
                    } else {
                        Log.i(TAG, "GATE DROP alreadyDown keyCode=${event.keyCode}")
                        GateOutcome(GateDecision.DROP_ALREADY_DOWN, null)
                    }
                }
                KeyEvent.ACTION_UP -> {
                    if (pressedKeyCodes.remove(event.keyCode)) {
                        Log.i(TAG, "GATE PASS up keyCode=${event.keyCode}")
                        GateOutcome(GateDecision.PASS_UP, "up")
                    } else {
                        Log.i(TAG, "GATE DROP noMatchingDown keyCode=${event.keyCode}")
                        GateOutcome(GateDecision.DROP_NO_MATCHING_DOWN, null)
                    }
                }
                else -> GateOutcome(GateDecision.DROP_IGNORED, null)
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

/** gate 決策結果（label 為診斷歷史與 log 用的短字串）。 */
enum class GateDecision(val label: String) {
    PASS_DOWN("PASS"),
    PASS_UP("PASS"),
    DROP_REPEAT("DROP_REPEAT"),
    DROP_ALREADY_DOWN("DROP_ALREADY_DOWN"),
    DROP_NO_MATCHING_DOWN("DROP_NO_MATCHING_DOWN"),
    DROP_IGNORED("DROP_IGNORED"),
}