import 'package:dio/dio.dart';
import 'api_service.dart';
import '../models/Exercise.dart';
import '../models/Plan.dart';

class DataService {
  final ApiService _apiService = ApiService();
  final String uid;

  DataService(this.uid);

  // --- PLANS ---

  Future<void> savePlan(Plan plan) async {
    try {
      // We store plans under /users/{uid}/plans
      // Using POST generates a unique ID for the plan automatically
      await _apiService.dio.post(
        '/users/$uid/plans.json',
        data: plan.toMap(),
      );
    } catch (e) {
      rethrow;
    }
  }

  Future<List<Plan>> getPlans() async {
    try {
      final response = await _apiService.dio.get('/users/$uid/plans.json');
      if (response.data == null) return [];
      
      Map<String, dynamic> data = Map<String, dynamic>.from(response.data);
      List<Plan> plans = [];
      data.forEach((key, value) {
        final plan = Plan.fromMap(Map<String, dynamic>.from(value));
        // You might want to store the firebase key in your model if needed
        plans.add(plan);
      });
      return plans;
    } catch (e) {
      rethrow;
    }
  }

  // --- EXERCISES ---

  Future<void> saveExercise(Exercise exercise) async {
    try {
      // Separate exercises by user UID and planId
      await _apiService.dio.post(
        '/users/$uid/exercises/${exercise.planId}.json',
        data: exercise.toMap(),
      );
    } catch (e) {
      rethrow;
    }
  }
}
