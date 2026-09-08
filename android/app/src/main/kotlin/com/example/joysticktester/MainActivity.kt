package com.example.joysticktester
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel

class MainActivity: FlutterActivity() {
    private val STREAM = "com.example.joysticktester/gamepad_events"
    private var eventSink: EventChannel.EventSink? = null
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, STREAM).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) { eventSink = events }
                override fun onCancel(arguments: Any?) { eventSink = null }
            }
        )
    }
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if(event!=null && isGamepadEvent(event.deviceId)){
            val action = when(event.action){ KeyEvent.ACTION_DOWN->"down"; KeyEvent.ACTION_UP->"up"; else->"unknown" }
            val map = mapOf("type" to "button","keyCode" to event.keyCode,"action" to action,"repeatCount" to event.repeatCount)
            try{ eventSink?.success(map) }catch(e:Exception){ Log.e("MainActivity","send error",e) }
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
