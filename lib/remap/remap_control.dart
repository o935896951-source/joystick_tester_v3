import 'dart:convert';

import 'package:flutter/services.dart';

class RemapStatus {
  const RemapStatus({
    required this.serviceEnabled,
    required this.filterKeyEventsAvailable,
    required this.gesturesSupported,
  });

  final bool serviceEnabled;
  final bool filterKeyEventsAvailable;
  final bool gesturesSupported;

  factory RemapStatus.fromMap(Map<dynamic, dynamic> m) => RemapStatus(
        serviceEnabled: m['serviceEnabled'] == true,
        filterKeyEventsAvailable: m['filterKeyEventsAvailable'] == true,
        gesturesSupported: m['gesturesSupported'] == true,
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

  static Future<List<String>> getKeyEventHistory() async {
    try {
      final dynamic v = await _channel.invokeMethod('getKeyEventHistory');
      if (v is List) return v.map((e) => e.toString()).toList();
      return const [];
    } catch (_) {
      return const [];
    }
  }

  static Future<bool> clearKeyEventHistory() async {
    try {
      return await _channel.invokeMethod('clearKeyEventHistory') == true;
    } catch (_) {
      return false;
    }
  }

  static Future<Map<String, int>> getKeyEventHistoryStats() async {
    try {
      final dynamic v = await _channel.invokeMethod('getKeyEventHistoryStats');
      if (v is Map) {
        return v.map((key, value) => MapEntry(key.toString(), (value as num).toInt()));
      }
      return const {};
    } catch (_) {
      return const {};
    }
  }

  static Future<Map<String, dynamic>> getRemapConfig() async {
    try {
      final dynamic v = await _channel.invokeMethod('getRemapConfig');
      if (v is String) {
        final dynamic d = jsonDecode(v);
        if (d is Map<String, dynamic>) return d;
      }
      return const {};
    } catch (_) {
      return const {};
    }
  }

  static Future<bool> saveRemapConfig(Map<String, dynamic> config) async {
    try {
      final bool ok = await _channel
              .invokeMethod('saveRemapConfig', {'config': jsonEncode(config)}) ==
          true;
      return ok;
    } catch (_) {
      return false;
    }
  }
}