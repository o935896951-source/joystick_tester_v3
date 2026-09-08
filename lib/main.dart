import 'package:flutter/material.dart';
import 'joystick_input.dart';
import 'remap/remap_home.dart';
import 'settings.dart';
import 'utils/key_mapping.dart';
import 'utils/user_key_bindings.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await UserKeyBindings.load();
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Joystick Tester v3 (PS)',
      theme: ThemeData(primarySwatch: Colors.blue),
      home: const JoystickHome(),
    );
  }
}

class JoystickHome extends StatefulWidget {
  const JoystickHome({Key? key}) : super(key: key);
  @override
  State<JoystickHome> createState() => _JoystickHomeState();
}

class _JoystickHomeState extends State<JoystickHome> {
  final JoystickInput _input = JoystickInput();
  Map<int, bool> pressed = {};
  List<String> log = [];

  @override
  void initState() {
    super.initState();
    _input.events.listen((e) {
      setState(() {
        if (e['type'] == 'button') {
          final kc = e['keyCode'] is int ? e['keyCode'] as int : int.parse(e['keyCode'].toString());
          final down = e['action'] == 'down';
          pressed[kc] = down;
          final name = GamepadKeyMapper.mapKey(kc);
          final custom = UserKeyBindings.getBinding(kc);
          log.insert(0, '${down ? "按下" : "放開"} $name${custom != null ? " (${custom})" : ""}');
          if (log.length > 200) log.removeLast();
        } else if (e['type'] == 'axis') {
          log.insert(0, '軸 ${e['axis']}: ${e['value']}');
          if (log.length > 200) log.removeLast();
        }
      });
    });
  }

  @override
  void dispose() {
    _input.dispose();
    super.dispose();
  }

  void _openSettings() async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => const SettingsPage()));
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('搖桿測試器 v3 (PS)'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings_accessibility),
            tooltip: '搖桿映射器',
            onPressed: () async {
              await Navigator.push(context, MaterialPageRoute(builder: (_) => const RemapHomePage()));
            },
          ),
          IconButton(icon: const Icon(Icons.settings), onPressed: _openSettings),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(12.0),
        child: Column(
          children: [
            // Simple 2D PS skin placeholder
            Card(
              child: SizedBox(
                height: 220,
                child: Center(child: Text('PS 手把 2D 視覺化（上傳資源後會顯示）')),
              ),
            ),
            const SizedBox(height: 8),
            Expanded(
              child: Card(
                child: ListView.builder(
                  itemCount: log.length,
                  itemBuilder: (context, idx) => ListTile(title: Text(log[idx])),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
