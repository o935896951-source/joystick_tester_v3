import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'remap_control.dart';

class RemapHomePage extends StatefulWidget {
  const RemapHomePage({Key? key}) : super(key: key);

  @override
  State<RemapHomePage> createState() => _RemapHomePageState();
}

/// 一筆 raw 診斷事件（原生回傳的一行文字 + 解析出的欄位）。
class _Ev {
  _Ev(this.raw) {
    for (final part in raw.split(' | ')) {
      final i = part.indexOf('=');
      if (i > 0) {
        _fields[part.substring(0, i)] = part.substring(i + 1);
      }
    }
  }

  final String raw;
  final Map<String, String> _fields = <String, String>{};

  String field(String key, [String fallback = '']) => _fields[key] ?? fallback;

  bool get isDown => field('action') == 'DOWN';
  bool get isDrop => field('gate').startsWith('DROP');
  bool get isPass => field('gate') == 'PASS';
  bool get isA11y => field('origin') == 'a11y';

  String get displayText =>
      '${field('timestamp')} | ${field('origin')}\n'
      'keyCode=${field('keyCode')} | logical=${field('logical')} | action=${field('action')}\n'
      'deviceId=${field('deviceId')} | repeat=${field('repeat')} | source=${field('source')}\n'
      'downTime=${field('downTime')} | eventTime=${field('eventTime')}\n'
      'gate=${field('gate')} | emitted=${field('emitted')}';
}

class _RemapHomePageState extends State<RemapHomePage> {
  RemapStatus? _status;
  List<_Ev> _events = [];
  Map<String, int> _stats = const {};
  String? _lastUpdated;
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

  String _nowLabel() {
    final t = DateTime.now();
    String two(int v) => v.toString().padLeft(2, '0');
    String three(int v) => v.toString().padLeft(3, '0');
    return '${two(t.hour)}:${two(t.minute)}:${two(t.second)}.${three(t.millisecond)}';
  }

  Future<void> _refresh() async {
    setState(() => _loading = true);
    await _loadData();
    if (mounted) {
      setState(() => _loading = false);
    }
  }

  Future<void> _silentRefresh() => _loadData();

  Future<void> _loadData() async {
    final s = await RemapControl.getStatus();
    final history = await RemapControl.getKeyEventHistory();
    final stats = await RemapControl.getKeyEventHistoryStats();
    if (mounted) {
      setState(() {
        _status = s;
        _events = history.map((raw) => _Ev(raw)).toList().reversed.toList();
        _stats = stats;
        _lastUpdated = _nowLabel();
      });
      final latest = _events.isEmpty ? 'none' : _events.first.raw;
      debugPrint('[GP-DIAG-HISTORY-DART] received count=${_events.length} latest=$latest');
    }
  }

  int _n(String key) => _stats[key] ?? 0;

  Future<void> _clearHistory() async {
    final ok = await RemapControl.clearKeyEventHistory();
    await _loadData();
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(ok ? '事件歷史已清除' : '清除失敗')),
      );
    }
  }

  Future<void> _copyAll() async {
    final n = _events.length;
    final text = _events.map((e) => e.raw).join('\n');
    await Clipboard.setData(ClipboardData(text: text));
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('已複製 $n 筆事件')),
      );
    }
  }

  Future<void> _copyOne(_Ev e) async {
    await Clipboard.setData(ClipboardData(text: e.raw));
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('已複製該筆事件')),
      );
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
                      subtitle: Text('新→舊（最多 40 筆，activity 與 a11y 各自保留）'),
                    ),
                    Text(
                      'RAW ${_n('raw')} | PASS ${_n('pass')} | DROP ${_n('drop')} | '
                      'UNKNOWN ${_n('unknown')}',
                      style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                    ),
                    Text(
                      'ACT ${_n('activity')} | A11Y ${_n('a11y')}',
                      style: const TextStyle(fontSize: 12, color: Colors.blueGrey),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '目前 ${_events.length} / 40 筆',
                      style: const TextStyle(fontSize: 12),
                    ),
                    Text(
                      '最後更新：${_lastUpdated ?? '—'}',
                      style: const TextStyle(fontSize: 12),
                    ),
                    const Divider(height: 12),
                    if (_events.isEmpty)
                      const Padding(
                        padding: EdgeInsets.all(12),
                        child: Text('（尚未收到）'),
                      )
                    else
                      ListView.builder(
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        itemCount: _events.length,
                        itemBuilder: (context, idx) {
                          final e = _events[idx];
                          return ListTile(
                            dense: true,
                            contentPadding: EdgeInsets.zero,
                            tileColor: e.isDrop
                                ? Colors.red.shade50
                                : (e.isPass ? Colors.green.shade50 : null),
                            leading: Icon(
                              e.isDown ? Icons.arrow_downward : Icons.arrow_upward,
                              size: 18,
                              color: e.isDrop ? Colors.red : Colors.blueGrey,
                            ),
                            title: Text(
                              e.displayText,
                              style: const TextStyle(fontSize: 11),
                            ),
                            trailing: IconButton(
                              icon: const Icon(Icons.copy, size: 18),
                              tooltip: '複製該筆',
                              onPressed: () => _copyOne(e),
                            ),
                          );
                        },
                      ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                OutlinedButton.icon(
                  onPressed: _loading ? null : _clearHistory,
                  icon: const Icon(Icons.delete_sweep),
                  label: const Text('清除歷史'),
                ),
                FilledButton.icon(
                  onPressed: _loading ? null : _copyAll,
                  icon: const Icon(Icons.copy_all),
                  label: const Text('複製全部'),
                ),
                OutlinedButton.icon(
                  onPressed: _loading ? null : _refresh,
                  icon: const Icon(Icons.refresh),
                  label: const Text('重新整理 / 診斷'),
                ),
              ],
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
                '診斷使用方式：\n'
                '1. 先按「清除歷史」\n'
                '2. 只按一次實體 A\n'
                '3. 等約 1 秒（每 700ms 自動刷新）\n'
                '4. 確認最新事件在最上面\n'
                '5. 按「複製全部」後貼給 ChatGPT 分析\n\n'
                '每一筆保留所有 RAW 欄位，gate DROP 也會留下，不加以合併或過濾。',
                style: TextStyle(fontSize: 14),
              ),
            ),
          ],
        ),
      ),
    );
  }
}