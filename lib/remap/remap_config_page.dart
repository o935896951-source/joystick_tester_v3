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
  192: 'L1 (SR-001) 192',
  193: 'R1 (SR-001) 193',
  194: 'L2 (SR-001) 194',
  195: 'R2 (SR-001) 195',
  96: 'BUTTON_A 96',
  97: 'BUTTON_B 97',
  99: 'BUTTON_X 99',
  100: 'BUTTON_Y 100',
  19: 'DPAD_UP 19',
  20: 'DPAD_DOWN 20',
  21: 'DPAD_LEFT 21',
  22: 'DPAD_RIGHT 22',
};

const _dpadId = 'DPAD';
const _dirUp = 'UP';
const _dirDown = 'DOWN';
const _dirLeft = 'LEFT';
const _dirRight = 'RIGHT';
const _directionIds = [_dirUp, _dirDown, _dirLeft, _dirRight];

/// 方向 marker 內縮係數：marker 落在 r*k 的位置，不貼圓周。
const _dpadInset = 0.9;

Map<String, dynamic> _defaultDpad() =>
    {'cx': 0.30, 'cy': 0.55, 'r': 0.18, 'visible': true, 'opacity': 0.4};

/// 由方向圈推導單一方向鍵的 (xRatio, yRatio)，並 clamp 到 0~1。
(double, double) _dpadPosition(String id, Map<String, dynamic> d) {
  final cx = ((d['cx'] ?? 0.30) as num).toDouble();
  final cy = ((d['cy'] ?? 0.55) as num).toDouble();
  final r = ((d['r'] ?? 0.18) as num).toDouble();
  final inset = r * _dpadInset;
  final x = id == _dirLeft
      ? cx - inset
      : id == _dirRight
          ? cx + inset
          : cx;
  final y = id == _dirUp
      ? cy - inset
      : id == _dirDown
          ? cy + inset
          : cy;
  return (x.clamp(0.0, 1.0), y.clamp(0.0, 1.0));
}

Map<String, dynamic> _defaultButtons() => {
      'A': {'physicalKeyCode': 190, 'xRatio': 0.86, 'yRatio': 0.60, 'sizeRatio': 0.11, 'visible': true, 'opacity': 0.6},
      'B': {'physicalKeyCode': 189, 'xRatio': 0.95, 'yRatio': 0.42, 'sizeRatio': 0.11, 'visible': true, 'opacity': 0.6},
      'X': {'physicalKeyCode': 191, 'xRatio': 0.77, 'yRatio': 0.42, 'sizeRatio': 0.11, 'visible': true, 'opacity': 0.6},
      'Y': {'physicalKeyCode': 188, 'xRatio': 0.86, 'yRatio': 0.24, 'sizeRatio': 0.11, 'visible': true, 'opacity': 0.6},
      'L1': {'physicalKeyCode': 192, 'xRatio': 0.18, 'yRatio': 0.16, 'sizeRatio': 0.10, 'visible': true, 'opacity': 0.6},
      'R1': {'physicalKeyCode': 193, 'xRatio': 0.82, 'yRatio': 0.16, 'sizeRatio': 0.10, 'visible': true, 'opacity': 0.6},
      'L2': {'physicalKeyCode': 194, 'xRatio': 0.18, 'yRatio': 0.28, 'sizeRatio': 0.10, 'visible': true, 'opacity': 0.6},
      'R2': {'physicalKeyCode': 195, 'xRatio': 0.82, 'yRatio': 0.28, 'sizeRatio': 0.10, 'visible': true, 'opacity': 0.6},
      _dirUp: {'physicalKeyCode': 19, 'xRatio': 0.30, 'yRatio': 0.388, 'sizeRatio': 0.095, 'visible': true, 'opacity': 0.4},
      _dirDown: {'physicalKeyCode': 20, 'xRatio': 0.30, 'yRatio': 0.712, 'sizeRatio': 0.095, 'visible': true, 'opacity': 0.4},
      _dirLeft: {'physicalKeyCode': 21, 'xRatio': 0.138, 'yRatio': 0.55, 'sizeRatio': 0.09, 'visible': true, 'opacity': 0.4},
      _dirRight: {'physicalKeyCode': 22, 'xRatio': 0.462, 'yRatio': 0.55, 'sizeRatio': 0.09, 'visible': true, 'opacity': 0.4},
    };

class _RemapConfigPageState extends State<RemapConfigPage> {
  Map<String, dynamic> _buttons = {};
  Map<String, dynamic> _dpad = {};
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
    final stored = <String, dynamic>{};
    final vb = cfg['virtualButtons'];
    if (vb is Map) {
      for (final e in vb.entries) {
        final v = e.value;
        if (v is Map) {
          stored[e.key.toString()] =
              v.map((k, x) => MapEntry(k.toString(), x));
        }
      }
    }
    final dpm = cfg['virtualDpad'];
    final dpadStored = dpm is Map
        ? dpm.map((k, x) => MapEntry(k.toString(), x))
        : <String, dynamic>{};
    // 一律從 12 鍵預設起手再覆蓋舊設定，補上舊版缺少的方向鍵。
    final merged = _defaultButtons();
    merged.addAll(stored);
    final dpad = <String, dynamic>{}
      ..addAll(_defaultDpad())
      ..addAll(dpadStored);
    setState(() {
      _buttons = merged;
      _dpad = dpad;
      _loaded = true;
      _selected = _buttons.keys.first;
      _syncDirectionButtons();
    });
  }

  Map<String, dynamic> _sel() => _buttons[_selected] as Map<String, dynamic>;

  double _r(String k, double fallback) =>
      ((_sel()[k] ?? fallback) as num).toDouble();

  double _doubleOf(Map<String, dynamic> m, String k, double fallback) =>
      ((m[k] ?? fallback) as num).toDouble();

  /// 相容 Kotlin 存的 JSON bool 與本頁寫入的 1.0/0.0 兩種型別。
  bool _visibleOf(Map<String, dynamic> b) {
    final v = b['visible'] ?? true;
    if (v is bool) return v;
    if (v is num) return v != 0.0;
    return true;
  }

  void _setR(String k, double v) => setState(() => (_sel())[k] = v);

  /// 把方向圈套用到四個方向鍵：位置由 [cx,cy,r] 推導，顯示/透明度一起同步。
  void _syncDirectionButtons() {
    for (final id in _directionIds) {
      final p = _dpadPosition(id, _dpad);
      final b = _buttons.putIfAbsent(
          id,
          () => {
                'physicalKeyCode':
                    id == _dirUp ? 19 : id == _dirDown ? 20 : id == _dirLeft ? 21 : 22,
                'xRatio': p.$1,
                'yRatio': p.$2,
                'sizeRatio': id == _dirLeft || id == _dirRight ? 0.09 : 0.095,
                'visible': true,
                'opacity': 0.4,
              });
      b['xRatio'] = p.$1;
      b['yRatio'] = p.$2;
      b['visible'] = _visibleOf(_dpad) ? 1.0 : 0.0;
      b['opacity'] = _doubleOf(_dpad, 'opacity', 0.4);
    }
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    _syncDirectionButtons();
    final ok = await RemapControl.saveRemapConfig(
        {'virtualButtons': _buttons, 'virtualDpad': _dpad});
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
    // 四個方向鍵不各自獨立編輯，只透過「方向區」圓圈調整。
    final ids = _buttons.keys.where((e) => !_directionIds.contains(e)).toList();

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
                        _dpadWidget(w, h),
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
                ChoiceChip(
                  label: const Text('方向區'),
                  selected: _selected == _dpadId,
                  selectedColor: Colors.green.shade100,
                  onSelected: (_) => setState(() => _selected = _dpadId),
                ),
              ],
            ),
            const SizedBox(height: 4),
            if (_selected == _dpadId) ..._dpadControls() else ...[
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
            ],
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
                  onPressed: () {
                    setState(() {
                      _buttons = _defaultButtons();
                      _dpad = _defaultDpad();
                      _selected = 'A';
                      _syncDirectionButtons();
                    });
                  },
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

  /// 方向區選取時的面板：大小、透明度、啟用開關（方向鍵 keyCode 固定，不可改）。
  List<Widget> _dpadControls() {
    final r = _doubleOf(_dpad, 'r', 0.18);
    final opacity = _doubleOf(_dpad, 'opacity', 0.4);
    return [
      const Text(
        '方向區：四個方向鍵位置由這個圓圈自動推算。'
        '拖曳圓圈移動中心，下方滑桿調整大小。',
        style: TextStyle(fontSize: 13),
      ),
      const SizedBox(height: 8),
      Text('方向區大小：${(r * 100).toStringAsFixed(0)}% 寬',
          style: const TextStyle(fontSize: 13)),
      Slider(
        value: r.clamp(0.10, 0.30),
        min: 0.10,
        max: 0.30,
        onChanged: (v) {
          setState(() {
            _dpad['r'] = v;
            _syncDirectionButtons();
          });
        },
      ),
      Text('透明度：${(opacity * 100).toStringAsFixed(0)}%',
          style: const TextStyle(fontSize: 13)),
      Slider(
        value: opacity.clamp(0.1, 1.0),
        min: 0.1,
        max: 1.0,
        onChanged: (v) {
          setState(() {
            _dpad['opacity'] = v;
            _syncDirectionButtons();
          });
        },
      ),
      SwitchListTile(
        contentPadding: EdgeInsets.zero,
        title: const Text('啟用方向區'),
        value: _visibleOf(_dpad),
        onChanged: (v) {
          setState(() {
            _dpad['visible'] = v ? 1.0 : 0.0;
            _syncDirectionButtons();
          });
        },
      ),
    ];
  }

  /// 預覽上的方向圈：可拖曳移動中心，畫出圓環、中心點與四個方向 marker。
  Widget _dpadWidget(double w, double h) {
    final cx = _doubleOf(_dpad, 'cx', 0.30);
    final cy = _doubleOf(_dpad, 'cy', 0.55);
    final r = _doubleOf(_dpad, 'r', 0.18);
    final visible = _visibleOf(_dpad);
    final opacity = _doubleOf(_dpad, 'opacity', 0.4);
    final selected = _selected == _dpadId;
    // 圓環橫軸以寬度比例、縱軸以高度比例，與存入的 xRatio/yRatio 一致。
    final rx = r * w;
    final ry = r * h;
    final pad = (rx > ry ? rx : ry) * 0.25;
    final boxW = (rx + pad) * 2;
    final boxH = (ry + pad) * 2;
    return Positioned(
      left: (cx * w - rx - pad).clamp(0.0, w - boxW),
      top: (cy * h - ry - pad).clamp(0.0, h - boxH),
      width: boxW,
      height: boxH,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onPanStart: (_) => setState(() => _selected = _dpadId),
        onPanUpdate: (d) {
          setState(() {
            final rr = _doubleOf(_dpad, 'r', 0.18);
            final nx = cx + d.delta.dx / w;
            final ny = cy + d.delta.dy / h;
            _dpad['cx'] = nx.clamp(rr, 1 - rr);
            _dpad['cy'] = ny.clamp(rr, 1 - rr);
            _syncDirectionButtons();
          });
        },
        child: CustomPaint(
          size: Size(boxW, boxH),
          painter: _DirectionCirclePainter(
            center: Offset(rx + pad, ry + pad),
            rx: rx,
            ry: ry,
            color: visible
                ? (selected ? Colors.green : Colors.lightGreen).withValues(alpha: opacity)
                : const Color(0x33000000),
            outline: selected ? Colors.white : Colors.white70,
          ),
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

/// 方向圈繪製：圓環、中心點、與落在 r*0.9 上的四個方向箭頭。
class _DirectionCirclePainter extends CustomPainter {
  _DirectionCirclePainter({
    required this.center,
    required this.rx,
    required this.ry,
    required this.color,
    required this.outline,
  });

  final Offset center;
  final double rx;
  final double ry;
  final Color color;
  final Color outline;

  @override
  void paint(Canvas canvas, Size size) {
    final ringRect = Rect.fromCenter(
      center: center,
      width: rx * 2,
      height: ry * 2,
    );
    final ring = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2
      ..color = outline;
    canvas.drawOval(ringRect, ring);
    if (color.a > 0) {
      final fill = Paint()..color = color;
      canvas.drawOval(ringRect, fill);
    }
    // 中心點
    final dot = Paint()..color = outline;
    canvas.drawCircle(center, (rx > ry ? rx : ry) * 0.10, dot);
    final labelColor = color.a > 0 ? Colors.white : Colors.white38;
    final arrow = (rx > ry ? rx : ry) * 0.28;
    _drawArrow(canvas, '↑', center + Offset(0, -ry * _dpadInset), arrow, labelColor);
    _drawArrow(canvas, '↓', center + Offset(0, ry * _dpadInset), arrow, labelColor);
    _drawArrow(canvas, '←', center + Offset(-rx * _dpadInset, 0), arrow, labelColor);
    _drawArrow(canvas, '→', center + Offset(rx * _dpadInset, 0), arrow, labelColor);
  }

  void _drawArrow(Canvas canvas, String text, Offset at, double size, Color color) {
    final tp = TextPainter(
      text: TextSpan(
        text: text,
        style: TextStyle(
          color: color,
          fontWeight: FontWeight.bold,
          fontSize: size,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    tp.paint(canvas, at - Offset(tp.width / 2, tp.height / 2));
  }

  @override
  bool shouldRepaint(covariant _DirectionCirclePainter old) =>
      old.center != center ||
      old.rx != rx ||
      old.ry != ry ||
      old.color != color ||
      old.outline != outline;
}