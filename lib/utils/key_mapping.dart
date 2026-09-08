class GamepadKeyMapper {
  static const Map<int, String> androidKeyMap = {
    96: "A",
    97: "B",
    99: "X",
    100: "Y",
    102: "L1",
    103: "R1",
    104: "L2",
    105: "R2",
    106: "L3",
    107: "R3",
    108: "START",
    109: "SELECT",
    110: "MODE",
    19: "DPAD_UP",
    20: "DPAD_DOWN",
    21: "DPAD_LEFT",
    22: "DPAD_RIGHT",
    85: "PLAY_PAUSE",
    62: "SPACE",
    23: "ENTER",
  };
  static String mapKey(int keyCode) => androidKeyMap[keyCode] ?? "未知($keyCode)";
}
