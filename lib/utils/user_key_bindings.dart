import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

class UserKeyBindings {
  static const _prefsKey = 'user_key_bindings_v3';
  static Map<int, String> _bindings = {};

  static Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    final s = prefs.getString(_prefsKey);
    if (s == null || s.isEmpty) _bindings = {};
    else {
      final Map<String,dynamic> m = jsonDecode(s);
      _bindings = m.map((k,v) => MapEntry(int.parse(k), v as String));
    }
  }

  static Future<void> save() async {
    final prefs = await SharedPreferences.getInstance();
    final m = _bindings.map((k,v) => MapEntry(k.toString(), v));
    await prefs.setString(_prefsKey, jsonEncode(m));
  }

  static void bindKey(int keyCode, String actionName) => _bindings[keyCode]=actionName;
  static void unbindKey(int keyCode) => _bindings.remove(keyCode);
  static String? getBinding(int keyCode) => _bindings[keyCode];
  static Map<int,String> get allBindings => Map.from(_bindings);
}
