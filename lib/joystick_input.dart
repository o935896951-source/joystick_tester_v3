import 'dart:async';
import 'package:flutter/services.dart';

class JoystickInput {
  static const EventChannel _channel = EventChannel('com.example.joysticktester/gamepad_events');
  final StreamController<Map<String, dynamic>> _controller = StreamController.broadcast();
  Stream<Map<String, dynamic>> get events => _controller.stream;
  StreamSubscription? _sub;

  JoystickInput() {
    try {
      _sub = _channel.receiveBroadcastStream().listen((dynamic event) {
        try {
          final Map<dynamic, dynamic> e = event;
          final map = <String, dynamic>{};
          e.forEach((k, v) => map[k.toString()] = v);
          _controller.add(map);
        } catch (err) {}
      }, onError: (err) {});
    } catch (e) {}
  }

  void dispose() {
    _sub?.cancel();
    _controller.close();
  }
}
