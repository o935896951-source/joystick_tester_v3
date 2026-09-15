import 'package:flutter/material.dart';

import 'remap_control.dart';

/// A/B/X/Y 虛擬觸控按鈕配置器。
/// 位置/大小都用 0~1 比例座標，儲存在原生 SharedPreferences（經 RemapControl）。
class RemapConfigPage extends StatefulWidget {
  const RemapConfigPage({Key? key}) : super(key: key);

  @override
  State<RemapConfigPage> createState() => _RemapConfigPageState();
}

const _keyLabels = <int, String>{
  188: 'Y (SR-001) 188',
  189: 'B (SR-001) 189',
  190: 'A (SR-001) 190',
  191: 'X (SR-001) 191',
  96: 'BUTTON_A 96',
  97: 'BUTTON_B 97',
  99: 'BUTTON_X 99',
  100: 'BUTTON_Y 100',
  19: 'DPAD_UP 19',
  20: 'DPAD_DOWN 20',
  21: 'DPAD_LEFT 21',
  22: 'DPAD_RIGHT 22',
};

Map<String, dynamic> _defaultButtons() => {
      'A': {'physicalKeyCode': 190, 'xRatio': 0.86, 'yRatio': 0.60, 'sizeRatio': 0.11, 'visible': true, 'opacity': 0.6},
      'B': {'physicalKeyCode': 189, 'xRatio': 0.95, 'yRatio': 0.42, 'sizeRatio': 0.11, 'visible': true, 'opacity': 0.6},
      'X': {'physicalKeyCode': 191, 'xRatio': 0.77, 'yRatio': 0.42, 'sizeRatio': 0.11, 'visible': true, 'opacity': 0.6},
      'Y': {'physicalKeyCode': 188, 'xRatio': 0.86, 'yRatio': 0.24, 'sizeRatio': 0.11, 'visible': true, 'opacity': 0.6},
    };

class _RemapConfigPageState extends State<RemapConfigPage> {
  Map<String, dynamic> _buttons = {};
  bool _loaded = false;
  bool _saving = false;
  String _selected = 'A';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final cfg = await RemapControl.getRemapConfig();
    final vb = cfg['virtualButtons'];
    if (vb is Map) {
      final m = <String, dynamic>{};
      for (final e in vb.entries) {
        final v = e.value;
        m[e.key.toString()] =
            v is Map ? v.map((k, x) => MapEntry(k.toString(), x)) : <String, dynamic>{};
      }
      if (m.isNotEmpty) {
        setState(() {
          _buttons = m;
          _loaded = true;
          _selected = _buttons.keys.first;
        });
        return;
      }
    }
    setState(() {
      _buttons = _defaultButtons();
      _loaded = true;
    });
  }

  Map<String, dynamic> _sel() => _buttons[_selected] as Map<String, dynamic>;

  double _r(String k, double fallback) =>
      ((_sel()[k] ?? fallback) as num).toDouble();

  /// 相容 Kotlin 存的 JSON bool 與本頁寫入的 1.0/0.0 兩種型別。
  bool _visibleOf(Map<String, dynamic> b) {
    final v = b['visible'] ?? true;
    if (v is bool) return v;
    if (v is num) return v != 0.0;
    return true;
  }

  void _setR(String k, double v) => setState(() => (_sel())[k] = v);

  Future<void> _save() async {
    setState(() => _saving = true);
    final ok = await RemapControl.saveRemapConfig({'virtualButtons': _buttons});
    setState(() => _saving = false);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(ok ? '已儲存虛擬按鈕設定' : '儲存失敗')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_loaded) {
      return Scaffold(
        appBar: AppBar(title: const Text('虛擬按鈕設定')),
        body: const Center(child: CircularProgressIndicator()),
      );
    }
    final screen = MediaQuery.of(context).size;
    final previewAspect = screen.width / screen.height;
    final ids = _buttons.keys.toList();

    return Scaffold(
      appBar: AppBar(title: const Text('虛擬按鈕設定')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              '拖曳按鈕到 Evil Lands 畫面上的位置，調整大小後按「儲存」。'
              '座標為比例，會依實際螢幕自動換算。',
              style: TextStyle(fontSize: 13),
            ),
            const SizedBox(height: 8),
            Card(
              clipBehavior: Clip.antiAlias,
              child: AspectRatio(
                aspectRatio: previewAspect,
                child: LayoutBuilder(
                  builder: (context, constraints) {
                    final w = constraints.maxWidth;
                    final h = constraints.maxHeight;
                    return Stack(
                      children: [
                        Positioned.fill(
                          child: ColoredBox(
                            color: const Color(0xFF263238),
                            child: const Center(
                              child: Text(
                                'Evil Lands 畫面預覽（等比例）',
                                style: TextStyle(color: Colors.white70, fontSize: 12),
                              ),
                            ),
                          ),
                        ),
                        for (final id in ids)
                          _buttonWidget(id, w, h, _buttons[id] as Map<String, dynamic>),
                      ],
                    );
                  },
                ),
              ),
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              children: [
                for (final id in ids)
                  ChoiceChip(
                    label: Text(id),
                    selected: _selected == id,
                    selectedColor: Colors.blue.shade100,
                    onSelected: (_) => setState(() => _selected = id),
                  ),
              ],
            ),
            const SizedBox(height: 4),
            Row(
              children: [
                const Text('實體按鍵：', style: TextStyle(fontSize: 14)),
                Expanded(
                  child: DropdownButtonFormField<int>(
                    initialValue: _r('physicalKeyCode', 0).toInt(),
                    isExpanded: true,
                    decoration: const InputDecoration(isDense: true),
                    items: _keyLabels.entries
                        .map((e) => DropdownMenuItem(
                              value: e.key,
                              child: Text(e.value, overflow: TextOverflow.ellipsis),
                            ))
                        .toList(),
                    onChanged: (v) {
                      if (v != null) _setR('physicalKeyCode', v.toDouble());
                    },
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text('按鈕大小：${(_r('sizeRatio', 0.11) * 100).toStringAsFixed(0)}% 寬',
                style: const TextStyle(fontSize: 13)),
            Slider(
              value: _r('sizeRatio', 0.11).clamp(0.04, 0.30),
              min: 0.04,
              max: 0.30,
              onChanged: (v) => _setR('sizeRatio', v),
            ),
            Text('透明度：${(_r('opacity', 0.6) * 100).toStringAsFixed(0)}%',
                style: const TextStyle(fontSize: 13)),
            Slider(
              value: _r('opacity', 0.6).clamp(0.1, 1.0),
              min: 0.1,
              max: 1.0,
              onChanged: (v) => _setR('opacity', v),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('啟用此虛擬按鈕'),
              value: _visibleOf(_sel()),
              onChanged: (v) => _setR('visible', v ? 1.0 : 0.0),
            ),
            const SizedBox(height: 4),
            Row(
              children: [
                Expanded(
                  child: FilledButton.icon(
                    onPressed: _saving ? null : _save,
                    icon: const Icon(Icons.save),
                    label: Text(_saving ? '儲存中…' : '儲存'),
                  ),
                ),
                const SizedBox(width: 8),
                OutlinedButton(
                  onPressed: () => setState(() => _buttons = _defaultButtons()),
                  child: const Text('重設預設'),
                ),
              ],
            ),
            const SizedBox(height: 8),
            const Text(
              '顯示/透明度目前主要用於視覺與測試：dispatchGesture 只依座標注入觸控，'
              '可先不顯示按鈕。儲存後請回到「搖桿映射器」確認提供無障礙服務後進遊戲測試。',
              style: TextStyle(fontSize: 12, color: Colors.blueGrey),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buttonWidget(String id, double w, double h, Map<String, dynamic> b) {
    final x = ((b['xRatio'] ?? 0.5) as num).toDouble();
    final y = ((b['yRatio'] ?? 0.5) as num).toDouble();
    final size = ((b['sizeRatio'] ?? 0.1) as num).toDouble() * w;
    final visible = _visibleOf(b);
    final opacity = ((b['opacity'] ?? 0.6) as num).toDouble();
    final selected = _selected == id;
    return Positioned(
      left: (x * w - size / 2).clamp(0.0, w - size),
      top: (y * h - size / 2).clamp(0.0, h - size),
      width: size,
      height: size,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onPanStart: (_) => setState(() => _selected = id),
        onPanUpdate: (d) {
          setState(() {
            final nx = ((b['xRatio'] ?? 0.5) as num).toDouble() + d.delta.dx / w;
            final ny = ((b['yRatio'] ?? 0.5) as num).toDouble() + d.delta.dy / h;
            b['xRatio'] = nx.clamp(0.0, 1.0);
            b['yRatio'] = ny.clamp(0.0, 1.0);
          });
        },
        child: Container(
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: visible
                ? (selected ? Colors.blue : Colors.blueGrey).withValues(alpha: opacity)
                : const Color(0x33000000),
            border: Border.all(
              color: selected ? Colors.white : Colors.white38,
              width: 2,
            ),
          ),
          child: Center(
            child: Text(
              id,
              style: TextStyle(
                color: visible ? Colors.white : Colors.white38,
                fontWeight: FontWeight.bold,
                fontSize: size * 0.35,
              ),
            ),
          ),
        ),
      ),
    );
  }
}