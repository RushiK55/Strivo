import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'api_service.dart';

class AuthService {
  final ApiService _apiService = ApiService();

  Future<Map<String, dynamic>?> login(String email, String password) async {
    try {
      final response = await _apiService.dio.get(
        '/users.json',
        queryParameters: {
          'orderBy': '"email"',
          'equalTo': '"$email"',
        },
      );

      if (response.data != null && response.data is Map && (response.data as Map).isNotEmpty) {
        Map<String, dynamic> users = Map<String, dynamic>.from(response.data);
        String uid = users.keys.first;
        Map<String, dynamic> userData = Map<String, dynamic>.from(users[uid]);

        if (userData['password'] == password) {
          userData['uid'] = uid;
          return userData;
        }
      }
      return null;
    } on DioException catch (e) {
      debugPrint("Login Dio Error: ${e.response?.data}");
      rethrow;
    } catch (e) {
      debugPrint("Login General Error: $e");
      rethrow;
    }
  }

  Future<String?> register(String name, String email, String password) async {
    try {
      // 1. Check if email already exists
      // Note: This WILL fail if you haven't added ".indexOn": ["email"] in Firebase Rules
      try {
        final checkResponse = await _apiService.dio.get(
          '/users.json',
          queryParameters: {
            'orderBy': '"email"',
            'equalTo': '"$email"',
          },
        );

        if (checkResponse.data != null && 
            checkResponse.data is Map && 
            (checkResponse.data as Map).isNotEmpty) {
          throw Exception("Email already registered");
        }
      } on DioException catch (e) {
        // If it's a 400 error, it's likely missing indexes.
        // We log it but proceed to try POSTing anyway for debugging.
        debugPrint("Check Email Dio Error: ${e.response?.data}");
        if (e.response?.statusCode != 400) rethrow; 
      }

      // 2. Create new user
      final response = await _apiService.dio.post(
        '/users.json',
        data: {
          'name': name,
          'email': email,
          'password': password,
          'createdAt': DateTime.now().toIso8601String(),
        },
      );

      debugPrint("Register Response: ${response.data}");

      if (response.statusCode == 200 || response.statusCode == 201) {
        return response.data['name'];
      }
      return null;
    } on DioException catch (e) {
      debugPrint("Register Dio Error: ${e.response?.data}");
      rethrow;
    } catch (e) {
      debugPrint("Register General Error: $e");
      rethrow;
    }
  }
}
