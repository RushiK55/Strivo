import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../services/auth_service.dart';
import '../services/user_manager.dart';

class AuthProvider with ChangeNotifier {
  final AuthService _authService = AuthService();
  String? _uid;
  String? _userName;
  String? _userEmail;
  bool _isLoading = false;
  bool _isProfileComplete = false;

  String? get uid => _uid;
  String? get userName => _userName;
  String? get userEmail => _userEmail;
  bool get isLoading => _isLoading;
  bool get isAuthenticated => _uid != null;
  bool get isProfileComplete => _isProfileComplete;

  AuthProvider() {
    _loadUserSession();
  }

  Future<void> _loadUserSession() async {
    final prefs = await SharedPreferences.getInstance();
    _uid = prefs.getString('user_uid');
    _userName = prefs.getString('user_name');
    _userEmail = prefs.getString('user_email');
    
    final profile = await UserManager.getProfile();
    _isProfileComplete = profile['isComplete'];
    
    notifyListeners();
  }

  Future<void> refreshProfileStatus() async {
    final profile = await UserManager.getProfile();
    _isProfileComplete = profile['isComplete'];
    notifyListeners();
  }

  Future<bool> login(String email, String password) async {
    _isLoading = true;
    notifyListeners();
    try {
      final userData = await _authService.login(email, password);
      if (userData != null) {
        _uid = userData['uid'];
        _userName = userData['name'];
        _userEmail = userData['email'];

        final prefs = await SharedPreferences.getInstance();
        await prefs.setString('user_uid', _uid!);
        await prefs.setString('user_name', _userName ?? "");
        await prefs.setString('user_email', _userEmail ?? "");
        
        final profile = await UserManager.getProfile();
        _isProfileComplete = profile['isComplete'];

        _isLoading = false;
        notifyListeners();
        return true;
      }
    } catch (e) {
      debugPrint("Login error: $e");
    }
    _isLoading = false;
    notifyListeners();
    return false;
  }

  Future<bool> register(String name, String email, String password) async {
    _isLoading = true;
    notifyListeners();
    try {
      final generatedUid = await _authService.register(name, email, password);
      if (generatedUid != null) {
        _uid = generatedUid;
        _userName = name;
        _userEmail = email;

        final prefs = await SharedPreferences.getInstance();
        await prefs.setString('user_uid', _uid!);
        await prefs.setString('user_name', _userName ?? "");
        await prefs.setString('user_email', _userEmail ?? "");

        final profile = await UserManager.getProfile();
        _isProfileComplete = profile['isComplete'];

        _isLoading = false;
        notifyListeners();
        return true;
      }
    } catch (e) {
      debugPrint("Register error: $e");
    }
    _isLoading = false;
    notifyListeners();
    return false;
  }

  Future<void> logout() async {
    _uid = null;
    _userName = null;
    _userEmail = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('user_uid');
    await prefs.remove('user_name');
    await prefs.remove('user_email');
    notifyListeners();
  }
}
