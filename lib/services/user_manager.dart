import 'package:shared_preferences/shared_preferences.dart';

class UserManager {
  static const String _keyGender = 'user_gender';
  static const String _keyAge = 'user_age';
  static const String _keyHeight = 'user_height';
  static const String _keyWeight = 'user_weight';
  static const String _keyHighWeight = 'user_high_weight';
  static const String _keyLowWeight = 'user_low_weight';
  static const String _keyLastWeightUpdate = 'last_weight_update';
  static const String _keyProfileComplete = 'is_profile_complete';

  static Future<void> saveProfile({
    required String gender,
    required int age,
    required double height,
    required double weight,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyGender, gender);
    await prefs.setInt(_keyAge, age);
    await prefs.setDouble(_keyHeight, height);
    await _updateWeight(weight);
    await prefs.setBool(_keyProfileComplete, true);
  }

  static Future<void> _updateWeight(double newWeight) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_keyWeight, newWeight);
    await prefs.setInt(_keyLastWeightUpdate, DateTime.now().millisecondsSinceEpoch);

    double currentHigh = prefs.getDouble(_keyHighWeight) ?? 0.0;
    double currentLow = prefs.getDouble(_keyLowWeight) ?? 1000.0;

    if (newWeight > currentHigh) await prefs.setDouble(_keyHighWeight, newWeight);
    if (newWeight < currentLow) await prefs.setDouble(_keyLowWeight, newWeight);
  }

  static Future<void> updateWeightOnly(double weight) async {
    await _updateWeight(weight);
  }

  static Future<Map<String, dynamic>> getProfile() async {
    final prefs = await SharedPreferences.getInstance();
    return {
      'gender': prefs.getString(_keyGender),
      'age': prefs.getInt(_keyAge),
      'height': prefs.getDouble(_keyHeight),
      'weight': prefs.getDouble(_keyWeight),
      'highWeight': prefs.getDouble(_keyHighWeight),
      'lowWeight': prefs.getDouble(_keyLowWeight),
      'isComplete': prefs.getBool(_keyProfileComplete) ?? false,
    };
  }

  static Future<bool> shouldAskWeight() async {
    final prefs = await SharedPreferences.getInstance();
    final lastUpdate = prefs.getInt(_keyLastWeightUpdate);
    if (lastUpdate == null) return true;

    final lastDate = DateTime.fromMillisecondsSinceEpoch(lastUpdate);
    final difference = DateTime.now().difference(lastDate).inDays;
    return difference >= 7;
  }
}
