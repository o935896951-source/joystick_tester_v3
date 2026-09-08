import 'package:flutter_test/flutter_test.dart';

import 'package:joystick_tester_v3_ps/main.dart';

void main() {
  testWidgets('App builds and shows title', (WidgetTester tester) async {
    await tester.pumpWidget(const MyApp());
    expect(find.byType(MyApp), findsOneWidget);
  });
}