package com.example.joysticktester
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val STREAM = "com.example.joysticktester/gamepad_events"
    private val REMAP_CONTROL = "com.example.joysticktester/remap_control"
    private var eventSink: EventChannel.EventSink? = null
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, STREAM).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) { eventSink = events }
                override fun onCancel(arguments: Any?) { eventSink = null }
            }
        )
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, REMAP_CONTROL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getRemapStatus" -> {
                    result.success(mapOf(
                        "serviceEnabled" to RemapAccessibilityService.isServiceEnabled(this),
                        "filterKeyEventsAvailable" to RemapAccessibilityService.filterKeyEventsAvailable,
                    ))
                }
                "openAccessibilitySettings" -> {
                    try {
                        startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        result.success(true)
                    } catch (e: Exception) {
                        result.success(false)
                    }
                }
                "getKeyEventHistory" -> {
                    result.success(RemapAccessibilityService.getKeyEventHistory())
                }
                else -> result.notImplemented()
            }
        }
    }
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if(event!=null && isGamepadEvent(event.deviceId)){
            val canonical = GamepadEventGate.ingestKeyEvent(event)
            if(canonical!=null){
                val map = mapOf("type" to "button","keyCode" to event.keyCode,"action" to canonical,"repeatCount" to event.repeatCount)
                try{ eventSink?.success(map) }catch(e:Exception){ Log.e("MainActivity","send error",e) }
                if(isGamepadEvent(event)){
                    val desc = "keyCode=${event.keyCode} " +
                        "action=${canonical.uppercase()} " +
                        "deviceId=${event.deviceId} " +
                        "repeatCount=${event.repeatCount}"
                    Log.i("RemapA11y","canonical $desc")
                    RemapAccessibilityService.recordKeyEvent(desc)
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
    override fun dispatchGenericMotionEvent(ev: MotionEvent?): Boolean {
        if(ev!=null && isGamepadEvent(ev.deviceId)){
            val axes=listOf(MotionEvent.AXIS_X,MotionEvent.AXIS_Y,MotionEvent.AXIS_Z,MotionEvent.AXIS_RZ,MotionEvent.AXIS_LTRIGGER,MotionEvent.AXIS_RTRIGGER,MotionEvent.AXIS_HAT_X,MotionEvent.AXIS_HAT_Y)
            try{ for(axis in axes){ val v=ev.getAxisValue(axis); if(Math.abs(v)>0.01f){ val map=mapOf("type" to "axis","axis" to axis,"value" to v); eventSink?.success(map) } } }catch(e:Exception){ Log.e("MainActivity","motion error",e) }
        }
        return super.dispatchGenericMotionEvent(ev)
    }
    private fun isGamepadEvent(deviceId:Int):Boolean{ try{ val dev=InputDevice.getDevice(deviceId) ?: return false; val sources=dev.sources; return (sources and InputDevice.SOURCE_GAMEPAD==InputDevice.SOURCE_GAMEPAD)||(sources and InputDevice.SOURCE_JOYSTICK==InputDevice.SOURCE_JOYSTICK) }catch(e:Exception){return false} }
}
