import 'package:flutter/material.dart';
import 'remap_control.dart';

class RemapHomePage extends StatefulWidget {
  const RemapHomePage({Key? key}) : super(key: key);

  @override
  State<RemapHomePage> createState() => _RemapHomePageState();
}

class _RemapHomePageState extends State<RemapHomePage> {
  RemapStatus? _status;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<void> _refresh() async {
    setState(() => _loading = true);
    final s = await RemapControl.getStatus();
    if (mounted) {
      setState(() {
        _status = s;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final enabled = _status?.serviceEnabled ?? false;
    return Scaffold(
      appBar: AppBar(title: const Text('搖桿映射器')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Card(
              child: ListTile(
                leading: Icon(
                  enabled ? Icons.check_circle : Icons.error_outline,
                  color: enabled ? Colors.green : Colors.orange,
                  size: 40,
                ),
                title: Text(enabled ? '無障礙服務已啟用' : '無障礙服務未啟用'),
                subtitle: Text(_status == null
                    ? '無法讀取狀態'
                    : (_status!.filterKeyEventsAvailable
                        ? '裝置支援按鍵全域攔截 (Android 8+)'
                        : '裝置不支援按鍵全域攔截')),
              ),
            ),
            const SizedBox(height: 16),
            ElevatedButton.icon(
              onPressed: _loading ? null : _openSettings,
              icon: const Icon(Icons.settings_accessibility),
              label: const Text('前往系統設定啟用無障礙服務'),
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.blueGrey.shade50,
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Text(
                '步驟：\n'
                '1. 點上方按鈕前往「無障礙」設定\n'
                '2. 找到「搖桿映射器」並開啟\n'
                '3. 回到本頁確認狀態為「已啟用」\n\n'
                '啟用後，App 會全域攔截實體搖桿方向鍵/按鍵，'
                '以便後續轉換為遊戲觸控。',
                style: TextStyle(fontSize: 14),
              ),
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: _loading ? null : _refresh,
              icon: const Icon(Icons.refresh),
              label: const Text('重新檢查狀態'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _openSettings() async {
    await RemapControl.openAccessibilitySettings();
  }
}