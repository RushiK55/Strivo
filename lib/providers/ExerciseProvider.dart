import 'package:flutter/cupertino.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/Exercise.dart';
import '../models/workout_session.dart';
import '../database/database_helper.dart';

class Exerciseprovider extends ChangeNotifier {
  List<Exercise> _exercises = [];
  List<Exercise> _todayExercises = [];
  List<Exercise> _extraExercises = [];
  List<WorkoutSession> _history = [];
  String _currentLoadedDay = "";

  List<Exercise> get exercises => _exercises;
  List<Exercise> get todayExercises => _todayExercises;
  List<Exercise> get extraExercises => _extraExercises;
  List<WorkoutSession> get history => _history;

  Future<void> fetchHistory() async {
    final sessionMaps = await DatabaseHelper.instance.getWorkoutSessions();
    _history = sessionMaps.map((map) => WorkoutSession.fromMap(map)).toList();
    notifyListeners();
  }

  Future<void> saveCompletedWorkout(String planName, String duration) async {
    List<PerformedExercise> performed = _exercises.map((ex) => PerformedExercise(
      name: ex.name,
      sets: ex.setsList.map((s) => s.toMap()).toList()
    )).toList();

    WorkoutSession newSession = WorkoutSession(
      planName: planName,
      date: DateTime.now(),
      totalTime: duration,
      exercises: performed
    );

    await DatabaseHelper.instance.saveSession(newSession.toMap());
    await fetchHistory();
  }

  Future<void> loadAllExercises() async {
    _exercises = await DatabaseHelper.instance.readAllExercises();
    notifyListeners();
  }

  Future<void> _checkAndResetDaily() async {
    final prefs = await SharedPreferences.getInstance();
    final lastReset = prefs.getString('last_daily_reset');
    final today = DateTime.now().toIso8601String().substring(0, 10); // YYYY-MM-DD

    if (lastReset != today) {
      await DatabaseHelper.instance.resetAllExerciseChecks();
      await DatabaseHelper.instance.deleteDailyExercises();
      await prefs.setString('last_daily_reset', today);
    }
  }

  Future<void> loadExercisesByDay(String dayName) async {
    _currentLoadedDay = dayName;
    await _checkAndResetDaily();
    _todayExercises = await DatabaseHelper.instance.readExercisesByDay(dayName);
    _extraExercises = await DatabaseHelper.instance.readExtraExercises();
    notifyListeners();
  }

  Future<void> loadTodayExercises() async {
    final now = DateTime.now();
    final dayName = _getDayName(now.weekday);
    await loadExercisesByDay(dayName);
  }

  String _getDayName(int weekday) {
    switch (weekday) {
      case 1: return "Monday";
      case 2: return "Tuesday";
      case 3: return "Wednesday";
      case 4: return "Thursday";
      case 5: return "Friday";
      case 6: return "Saturday";
      case 7: return "Sunday";
      default: return "";
    }
  }

  Future<void> loadExercisesByPlan(int planId) async {
    _exercises = await DatabaseHelper.instance.readExercisesByPlan(planId);
    notifyListeners();
  }

  Future<void> addExercise(Exercise e) async {
    await DatabaseHelper.instance.createExercise(e);
    await loadExercisesByPlan(e.planId);
    await loadExercisesByDay(_currentLoadedDay.isEmpty ? _getDayName(DateTime.now().weekday) : _currentLoadedDay);
  }

  Future<void> deleteExercise(int id, int planId) async {
    await DatabaseHelper.instance.deleteExercise(id);
    await loadExercisesByPlan(planId);
    await loadExercisesByDay(_currentLoadedDay.isEmpty ? _getDayName(DateTime.now().weekday) : _currentLoadedDay);
  }

  Future<void> updateExercise(Exercise e) async {
    await DatabaseHelper.instance.updateExercise(e);
    
    if (e.isCheck) {
      await DatabaseHelper.instance.addToHistory(e);
    } else {
      await DatabaseHelper.instance.removeFromHistory(e.name, DateTime.now().toIso8601String());
    }

    await loadExercisesByPlan(e.planId);
    await loadExercisesByDay(_currentLoadedDay.isEmpty ? _getDayName(DateTime.now().weekday) : _currentLoadedDay);
  }
}
