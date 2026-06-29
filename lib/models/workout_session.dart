import 'dart:convert';
import 'package:strivo/models/Exercise.dart';

class WorkoutSession {
  final int? id;
  final String planName;
  final DateTime date;
  final String totalTime;
  final List<PerformedExercise> exercises;

  WorkoutSession({
    this.id,
    required this.planName,
    required this.date,
    required this.totalTime,
    required this.exercises,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'planName': planName,
      'date': date.toIso8601String(),
      'totalTime': totalTime,
      'exercises': jsonEncode(exercises.map((e) => e.toMap()).toList()),
    };
  }

  factory WorkoutSession.fromMap(Map<String, dynamic> map) {
    return WorkoutSession(
      id: map['id'],
      planName: map['planName'],
      date: DateTime.parse(map['date']),
      totalTime: map['totalTime'],
      exercises: (jsonDecode(map['exercises']) as List)
          .map((x) => PerformedExercise.fromMap(x))
          .toList(),
    );
  }
}

class PerformedExercise {
  final String name;
  final List<Map<String, dynamic>> sets;

  PerformedExercise({required this.name, required this.sets});

  Map<String, dynamic> toMap() => {
        'name': name,
        'sets': sets,
      };

  factory PerformedExercise.fromMap(Map<String, dynamic> map) =>
      PerformedExercise(
        name: map['name'],
        sets: List<Map<String, dynamic>>.from(map['sets']),
      );
}
