package com.example.joysticktester

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent

/**
 * 實體手把按鍵 → 虛擬觸控按鈕（AccessibilityService.dispatchGesture）控制器。
 *
 * 設計：
 * - 只處理「有設定綁定、且 visible」的實體 keyCode；處理後由 service 端 return true 吞掉。
 * - pressed 集合負責「一次實體下壓 = 一次 touch down」的去重：repeatCount>0、已按下的重複
 *   DOWN、幽靈 UP 都不會造成重複 Touch。
 * - 按住：因為 dispatchGesture 的單一 stroke 是「下壓→停留→抬起」一次完成，本控制器用
 *   SEGMENT_MS 分段維持——每段結束（onCompleted/onCancelled）後若仍按住就派下一段。
 * - 序列化：同一時間只允許一個 dispatchGesture；事件只更新狀態模型 + revision，回呼時再
 *   依最新狀態重派，因此 A+B 同按會合併成同一個 gesture（多 stroke = 多指）。
 * - 座標：讀設定裡的 0~1 比例，派發時乘當下 displayMetrics 轉成螢幕 pixel。
 */
class RemapTouchController(private val service: AccessibilityService) {
    private val TAG = "RemapTouch"
    private val SEGMENT_MS = 50L
    private val RETRY_BASE_MS = 50L
    private val MAX_RETRY = 5

    /** callback 一律送回 main looper，與 onKeyEvent 同線程，避免狀態競態。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    private val pressed = mutableSetOf<Int>()
    private var revision = 0
    private var dispatching = false
    private var retryCount = 0

    private val displayWidth: Int
        get() = service.resources.displayMetrics.widthPixels
    private val displayHeight: Int
        get() = service.resources.displayMetrics.heightPixels

    /**
     * 回傳 true 表示已處理（呼叫端應 return true 吞掉，避免遊戲再收到原生按鍵）。
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val map = keycodeMap()
        val code = event.keyCode
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (!map.containsKey(code)) {
                    false
                } else if (event.repeatCount == 0 && pressed.add(code)) {
                    revision++
                    commit()
                    Log.i(TAG, "TOUCH DOWN keyCode=$code consumed")
                    true
                } else {
                    // 重複 DOWN / 已在按下：吞掉但不重複 Touch
                    true
                }
            }
            KeyEvent.ACTION_UP -> {
                if (pressed.remove(code)) {
                    revision++
                    commit()
                    Log.i(TAG, "TOUCH UP keyCode=$code consumed")
                    true
                } else if (map.containsKey(code)) {
                    // Ghost UP：已綁定（visible、config 內）但 pressed 無此 keyCode 的多餘 UP，
                    // 由 remapper 吞掉，避免放行給前景 App 產生「沒有 DOWN 的 UP」。
                    Log.i(TAG, "GHOST UP keyCode=$code bound, consumed")
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun keycodeMap(): Map<Int, RemapConfigStore.VirtualButton> {
        val out = HashMap<Int, RemapConfigStore.VirtualButton>()
        for (b in RemapConfigStore.loadMap(service.applicationContext).values) {
            if (b.visible) out[b.physicalKeyCode] = b
        }
        return out
    }

    private fun commit() {
        if (dispatching) return
        dispatchCurrent()
    }

    private fun dispatchCurrent() {
        if (pressed.isEmpty()) {
            dispatching = false
            retryCount = 0
            return
        }
        val desc = try {
            buildGesture()
        } catch (e: Exception) {
            Log.e(TAG, "buildGesture failed", e)
            null
        }
        if (desc == null) {
            dispatching = false
            retryCount = 0
            return
        }
        dispatching = true
        val rev = revision
        val accepted = try {
            service.dispatchGesture(
                desc,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) = segmentEnded(rev)
                    override fun onCancelled(g: GestureDescription?) = segmentEnded(rev)
                },
                mainHandler,
            )
        } catch (e: Exception) {
            // Bug C：例外時先解除 dispatching，不讓 exception 炸掉整個 service。
            Log.e(TAG, "dispatchGesture threw", e)
            false
        }
        if (accepted) {
            retryCount = 0
        } else {
            scheduleRetry(rev)
        }
    }

    /**
     * dispatchGesture 回 false 或丟例外時：先解除 dispatching（絕不永久卡死），
     * 再以退避延遲重試、且有上限；超過上限就回到 idle，等下一次真實按鍵自然觸發。
     * 避免無限高速 retry 造成事件/CPU 風暴。
     */
    private fun scheduleRetry(revAtDispatch: Int) {
        dispatching = false
        retryCount++
        if (retryCount > MAX_RETRY) {
            retryCount = 0
            Log.w(TAG, "dispatchGesture rejected repeatedly; waiting for next key event")
            return
        }
        val stateRev = revision
        mainHandler.postDelayed(
            {
                // 期間若已有新的派發（dispatching=true）或狀態已變過並被處理，則跳過。
                if (!dispatching && pressed.isNotEmpty() && revision == stateRev) {
                    dispatchCurrent()
                }
            },
            RETRY_BASE_MS * retryCount,
        )
    }

    private fun segmentEnded(revAtDispatch: Int) {
        dispatching = false
        if (revision != revAtDispatch || pressed.isNotEmpty()) {
            // 派發期間狀態變了，或玩家仍按住 → 依最新狀態重派（延續按住 / 吸收新按 / 移除放開）
            dispatchCurrent()
        }
    }

    private fun buildGesture(): GestureDescription? {
        val builder = GestureDescription.Builder()
        val map = keycodeMap()
        val usedButtons = mutableSetOf<String>()
        for (code in pressed) {
            val btn = map[code] ?: continue
            if (!usedButtons.add(btn.id)) continue // 同一個虛擬按鈕只放一根指頭
            val x = (btn.xRatio * displayWidth).toFloat()
            val y = (btn.yRatio * displayHeight).toFloat()
            val path = Path().apply { moveTo(x, y) }
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, SEGMENT_MS))
        }
        return if (usedButtons.isEmpty()) null else builder.build()
    }
}