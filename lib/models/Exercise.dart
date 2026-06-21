import 'dart:convert';

class ExerciseSet {
  double weight;
  int reps;
  bool isCompleted;
  bool isStarted;
  String restTime;
  String setDuration;

  ExerciseSet({
    required this.weight,
    required this.reps,
    this.isCompleted = false,
    this.isStarted = false,
    this.restTime = "",
    this.setDuration = "",
  });

  Map<String, dynamic> toMap() {
    return {
      'weight': weight,
      'reps': reps,
      'isCompleted': isCompleted,
      'isStarted': isStarted,
      'restTime': restTime,
      'setDuration': setDuration,
    };
  }

  factory ExerciseSet.fromMap(Map<String, dynamic> map) {
    return ExerciseSet(
      weight: (map['weight'] as num).toDouble(),
      reps: map['reps'] as int,
      isCompleted: map['isCompleted'] ?? false,
      isStarted: map['isStarted'] ?? false,
      restTime: map['restTime'] ?? "",
      setDuration: map['setDuration'] ?? "",
    );
  }
}

class Exercise {
  int? id; // SQLite will auto-increment this
  int planId; // -1 for extra exercises
  String name;
  String weight; // This might be used as a summary or legacy
  String sets;   // This might be used as a summary or legacy
  String reps;   // This might be used as a summary or legacy
  String notes;
  String dateTime;
  bool isCheck;
  bool isExtra;
  bool vanishEndOfDay;
  String restTime;
  List<ExerciseSet> setsList;

  Exercise({
    this.id,
    required this.planId,
    required this.name,
    required this.weight,
    required this.sets,
    required this.reps,
    required this.notes,
    this.dateTime = "",
    this.isCheck = false,
    this.isExtra = false,
    this.vanishEndOfDay = false,
    this.restTime = "",
    List<ExerciseSet>? setsList,
  }) : this.setsList = setsList ?? [];

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'planId': planId,
      'name': name,
      'weight': weight,
      'sets': sets,
      'reps': reps,
      'notes': notes,
      'dateTime': dateTime,
      'isCheck': isCheck ? 1 : 0,
      'isExtra': isExtra ? 1 : 0,
      'vanishEndOfDay': vanishEndOfDay ? 1 : 0,
      'restTime': restTime,
      'setsData': jsonEncode(setsList.map((s) => s.toMap()).toList()),
    };
  }

  factory Exercise.fromMap(Map<String, dynamic> map) {
    List<ExerciseSet> setsList = [];
    if (map['setsData'] != null && map['setsData'].toString().isNotEmpty) {
      try {
        final List<dynamic> decoded = jsonDecode(map['setsData']);
        setsList = decoded.map((s) => ExerciseSet.fromMap(s)).toList();
      } catch (e) {
        print("Error decoding setsData: $e");
      }
    }

    return Exercise(
      id: map['id'],
      planId: map['planId'] ?? -1,
      name: map['name'] ?? "",
      weight: map['weight'] ?? "0",
      sets: map['sets'] ?? "0",
      reps: map['reps'] ?? "0",
      notes: map['notes'] ?? "",
      dateTime: map['dateTime'] ?? "",
      isCheck: map['isCheck'] == 1,
      isExtra: map['isExtra'] == 1,
      vanishEndOfDay: map['vanishEndOfDay'] == 1,
      restTime: map['restTime'] ?? "",
      setsList: setsList,
    );
  }
}
