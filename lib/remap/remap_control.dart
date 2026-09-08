import 'package:flutter/services.dart';

class RemapStatus {
  const RemapStatus({required this.serviceEnabled, required this.filterKeyEventsAvailable});

  final bool serviceEnabled;
  final bool filterKeyEventsAvailable;

  factory RemapStatus.fromMap(Map<dynamic, dynamic> m) => RemapStatus(
        serviceEnabled: m['serviceEnabled'] == true,
        filterKeyEventsAvailable: m['filterKeyEventsAvailable'] == true,
      );
}

class RemapControl {
  static const MethodChannel _channel = MethodChannel('com.example.joysticktester/remap_control');

  static Future<RemapStatus?> getStatus() async {
    try {
      final dynamic m = await _channel.invokeMethod('getRemapStatus');
      if (m is Map<dynamic, dynamic>) return RemapStatus.fromMap(m);
      return null;
    } catch (_) {
      return null;
    }
  }

  static Future<bool> openAccessibilitySettings() async {
    try {
      final bool ok = await _channel.invokeMethod('openAccessibilitySettings') == true;
      return ok;
    } catch (_) {
      return false;
    }
  }
}