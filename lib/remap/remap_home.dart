import 'dart:async';

import 'package:flutter/material.dart';

import 'remap_control.dart';

class RemapHomePage extends StatefulWidget {
  const RemapHomePage({Key? key}) : super(key: key);

  @override
  State<RemapHomePage> createState() => _RemapHomePageState();
}

class _RemapHomePageState extends State<RemapHomePage> {
  RemapStatus? _status;
  List<String> _keyHistory = [];
  bool _loading = true;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _refresh();
    _timer =
        Timer.periodic(const Duration(milliseconds: 700), (_) => _silentRefresh());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _refresh() async {
    setState(() => _loading = true);
    await _loadData();
    if (mounted) {
      setState(() => _loading = false);
    }
  }

  Future<void> _silentRefresh() async {
    await _loadData();
  }

  Future<void> _loadData() async {
    final s = await RemapControl.getStatus();
    final history = await RemapControl.getKeyEventHistory();
    if (mounted) {
      setState(() {
        _status = s;
        _keyHistory = history.reversed.toList();
      });
    }
  }

  Future<void> _openSettings() async {
    await RemapControl.openAccessibilitySettings();
  }

  @override
  Widget build(BuildContext context) {
    final enabled = _status?.serviceEnabled ?? false;
    return Scaffold(
      appBar: AppBar(title: const Text('搖桿映射器')),
      body: SingleChildScrollView(
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
            Card(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: Icon(Icons.sensors, size: 40),
                      title: Text('搖桿按鍵事件歷史'),
                      subtitle: Text('新→舊（最多 40 筆）'),
                    ),
                    const Divider(height: 1),
                    if (_keyHistory.isEmpty)
                      const Padding(
                        padding: EdgeInsets.all(12),
                        child: Text('（尚未收到）'),
                      )
                    else
                      ListView.builder(
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        itemCount: _keyHistory.length,
                        itemBuilder: (context, idx) {
                          final e = _keyHistory[idx];
                          final down = e.split('\n').any((line) => line == 'DOWN');
                          return ListTile(
                            dense: true,
                            contentPadding: EdgeInsets.zero,
                            leading: Text(
                              down ? '↓' : '↑',
                              style: const TextStyle(
                                  fontSize: 18, fontWeight: FontWeight.bold),
                            ),
                            title: Text(e, style: const TextStyle(fontSize: 12)),
                          );
                        },
                      ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: _loading ? null : _refresh,
              icon: const Icon(Icons.refresh),
              label: const Text('重新檢查狀態 / 診斷'),
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
          ],
        ),
      ),
    );
  }
}