import 'package:flutter/material.dart';
import 'utils/key_mapping.dart';
import 'utils/user_key_bindings.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({Key? key}) : super(key: key);
  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  Map<int, String> bindings = {};

  @override
  void initState() {
    super.initState();
    bindings = UserKeyBindings.allBindings;
  }

  Future<void> _editBinding(int keyCode) async {
    final controller = TextEditingController(text: bindings[keyCode]);
    await showDialog(context: context, builder: (_) => AlertDialog(
      title: Text('設定綁定：${GamepadKeyMapper.mapKey(keyCode)}'),
      content: TextField(controller: controller, decoration: const InputDecoration(labelText: '動作名稱')),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('取消')),
        TextButton(onPressed: () async {
          final t = controller.text.trim();
          if (t.isEmpty) UserKeyBindings.unbindKey(keyCode);
          else UserKeyBindings.bindKey(keyCode, t);
          await UserKeyBindings.save();
          setState(() { bindings = UserKeyBindings.allBindings; });
          Navigator.pop(context);
        }, child: const Text('儲存')),
      ],
    ));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('按鍵對應設定')),
      body: Padding(
        padding: const EdgeInsets.all(12.0),
        child: Column(
          children: [
            ElevatedButton.icon(onPressed: () async {
              for (final k in bindings.keys.toList()) { UserKeyBindings.unbindKey(k); }
              await UserKeyBindings.save(); setState((){ bindings = UserKeyBindings.allBindings; });
            }, icon: const Icon(Icons.delete_forever), label: const Text('清除所有綁定')),
            const SizedBox(height: 12),
            Expanded(child: ListView(
              children: bindings.entries.map((e) => Card(child: ListTile(
                title: Text('${GamepadKeyMapper.mapKey(e.key)} (keyCode: ${e.key})'),
                subtitle: Text('動作：${e.value}'),
                trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                  IconButton(icon: const Icon(Icons.edit), onPressed: () => _editBinding(e.key)),
                  IconButton(icon: const Icon(Icons.delete), onPressed: () async {
                    UserKeyBindings.unbindKey(e.key); await UserKeyBindings.save(); setState(()=> bindings = UserKeyBindings.allBindings);
                  }),
                ]),
              ))).toList(),
            )),
          ],
        ),
      ),
    );
  }
}
