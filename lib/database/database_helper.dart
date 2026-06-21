import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';
import '../models/Exercise.dart';
import '../models/Plan.dart';

class DatabaseHelper {
  static final DatabaseHelper instance = DatabaseHelper._init();
  static Database? _database;

  DatabaseHelper._init();

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDB('strivo.db');
    return _database!;
  }

  Future<Database> _initDB(String filePath) async {
    final dbPath = await getDatabasesPath();
    final path = join(dbPath, filePath);

    return await openDatabase(
      path,
      version: 6, // Bumped version to 6 for Exercise Rest Time
      onCreate: _createDB,
      onUpgrade: _onUpgrade,
    );
  }

  Future _onUpgrade(Database db, int oldVersion, int newVersion) async {
    if (oldVersion < 2) {
      await db.execute('''
        CREATE TABLE IF NOT EXISTS history (
          historyId INTEGER PRIMARY KEY AUTOINCREMENT,
          name TEXT,
          weight TEXT,
          sets TEXT,
          reps TEXT,
          completedAt TEXT
        )
      ''');
    }
    if (oldVersion < 3) {
      await db.execute('ALTER TABLE exercises ADD COLUMN isExtra INTEGER DEFAULT 0');
      await db.execute('ALTER TABLE exercises ADD COLUMN vanishEndOfDay INTEGER DEFAULT 0');
    }
    if (oldVersion < 4) {
      await db.execute('ALTER TABLE exercises ADD COLUMN setsData TEXT');
    }
    if (oldVersion < 5) {
      await db.execute('ALTER TABLE history ADD COLUMN setsData TEXT');
    }
    if (oldVersion < 6) {
      await db.execute('ALTER TABLE exercises ADD COLUMN restTime TEXT DEFAULT ""');
      await db.execute('ALTER TABLE history ADD COLUMN restTime TEXT DEFAULT ""');
    }
  }

  Future _createDB(Database db, int version) async {
    await db.execute('''
      CREATE TABLE plans (
        planId INTEGER PRIMARY KEY AUTOINCREMENT,
        planName TEXT,
        planDay TEXT
      )
    ''');

    await db.execute('''
      CREATE TABLE exercises (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        planId INTEGER,
        name TEXT,
        weight TEXT,
        sets TEXT,
        reps TEXT,
        notes TEXT,
        dateTime TEXT,
        isCheck INTEGER,
        isExtra INTEGER DEFAULT 0,
        vanishEndOfDay INTEGER DEFAULT 0,
        restTime TEXT DEFAULT "",
        setsData TEXT
      )
    ''');

    await db.execute('''
      CREATE TABLE history (
        historyId INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT,
        weight TEXT,
        sets TEXT,
        reps TEXT,
        completedAt TEXT,
        restTime TEXT DEFAULT "",
        setsData TEXT
      )
    ''');
  }

  // --- CRUD for PLANS ---

  // Create
  Future<int> createPlan(Plan plan) async {
    final db = await instance.database;
    return await db.insert('plans', plan.toMap());
  }

  // Read (All)
  Future<List<Plan>> readAllPlans() async {
    final db = await instance.database;
    final result = await db.query('plans');
    return result.map((json) => Plan.fromMap(json)).toList();
  }

  // Read (Filtered by Day)
  Future<List<Exercise>> readExercisesByDay(String day) async {
    final db = await instance.database;
    final result = await db.rawQuery('''
      SELECT exercises.* FROM exercises
      JOIN plans ON exercises.planId = plans.planId
      WHERE plans.planDay = ?
    ''', [day]);
    return result.map((json) => Exercise.fromMap(json)).toList();
  }

  // Read (Single)
  Future<Plan?> getPlan(int id) async {
    final db = await instance.database;
    final maps = await db.query(
      'plans',
      where: 'planId = ?',
      whereArgs: [id],
    );

    if (maps.isNotEmpty) {
      return Plan.fromMap(maps.first);
    } else {
      return null;
    }
  }

  // Update
  Future<int> updatePlan(Plan plan) async {
    final db = await instance.database;
    return await db.update(
      'plans',
      plan.toMap(),
      where: 'planId = ?',
      whereArgs: [plan.planId],
    );
  }

  // Delete
  Future<int> deletePlan(int id) async {
    final db = await instance.database;
    // Also delete exercises associated with this plan
    await db.delete(
      'exercises',
      where: 'planId = ?',
      whereArgs: [id],
    );
    return await db.delete(
      'plans',
      where: 'planId = ?',
      whereArgs: [id],
    );
  }

  // --- CRUD for EXERCISES ---

  // Create
  Future<int> createExercise(Exercise exercise) async {
    final db = await instance.database;
    return await db.insert('exercises', exercise.toMap());
  }

  // Read (All)
  Future<List<Exercise>> readAllExercises() async {
    final db = await instance.database;
    final result = await db.query('exercises');
    return result.map((json) => Exercise.fromMap(json)).toList();
  }

  // Read (Filtered by Plan)
  Future<List<Exercise>> readExercisesByPlan(int planId) async {
    final db = await instance.database;
    final result = await db.query(
      'exercises',
      where: 'planId = ?',
      whereArgs: [planId],
    );
    return result.map((json) => Exercise.fromMap(json)).toList();
  }

  Future<List<Exercise>> readExtraExercises() async {
    final db = await instance.database;
    final result = await db.query(
      'exercises',
      where: 'isExtra = ?',
      whereArgs: [1],
    );
    return result.map((json) => Exercise.fromMap(json)).toList();
  }

  // Read (Single)
  Future<Exercise?> getExercise(int id) async {
    final db = await instance.database;
    final maps = await db.query(
      'exercises',
      where: 'id = ?',
      whereArgs: [id],
    );

    if (maps.isNotEmpty) {
      return Exercise.fromMap(maps.first);
    } else {
      return null;
    }
  }

  // Update
  Future<int> updateExercise(Exercise exercise) async {
    final db = await instance.database;
    return await db.update(
      'exercises',
      exercise.toMap(),
      where: 'id = ?',
      whereArgs: [exercise.id],
    );
  }

  Future<int> resetAllExerciseChecks() async {
    final db = await instance.database;
    return await db.update(
      'exercises',
      {
        'isCheck': 0,
        'setsData': '', // Clear the sets logged in the previous session
        'weight': '0',
        'sets': '0',
        'reps': '0'
      },
    );
  }

  Future<int> deleteDailyExercises() async {
    final db = await instance.database;
    return await db.delete(
      'exercises',
      where: 'vanishEndOfDay = ? OR isExtra = ?',
      whereArgs: [1, 1], // Delete both "vanish" and "extra" exercises
    );
  }

  // Delete
  Future<int> deleteExercise(int id) async {
    final db = await instance.database;
    return await db.delete(
      'exercises',
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  // --- Personal Records ---
  Future<Map<String, double>> getPersonalRecords() async {
    final db = await instance.database;
    final result = await db.rawQuery('''
      SELECT 
        MAX(CAST(weight AS REAL)) as maxWeight,
        MAX(CAST(reps AS INTEGER)) as maxReps,
        MAX(CAST(weight AS REAL) * CAST(sets AS INTEGER) * CAST(reps AS INTEGER)) as maxVolume
      FROM exercises
    ''');

    if (result.isNotEmpty && result.first['maxWeight'] != null) {
      return {
        'maxWeight': (result.first['maxWeight'] as num).toDouble(),
        'maxReps': (result.first['maxReps'] as num).toDouble(),
        'maxVolume': (result.first['maxVolume'] as num).toDouble(),
      };
    }
    return {
      'maxWeight': 0.0,
      'maxReps': 0.0,
      'maxVolume': 0.0,
    };
  }

  // --- History ---
  Future<int> addToHistory(Exercise exercise) async {
    final db = await instance.database;
    final map = exercise.toMap();
    return await db.insert('history', {
      'name': exercise.name,
      'weight': exercise.weight,
      'sets': exercise.sets,
      'reps': exercise.reps,
      'completedAt': DateTime.now().toIso8601String(),
      'restTime': exercise.restTime,
      'setsData': map['setsData'],
    });
  }

  Future<int> removeFromHistory(String name, String date) async {
    final db = await instance.database;
    // This is a simple logic to remove if unchecked on the same day
    return await db.delete(
      'history',
      where: 'name = ? AND completedAt LIKE ?',
      whereArgs: [name, '${date.substring(0, 10)}%'],
    );
  }

  Future<List<Map<String, dynamic>>> getHistory() async {
    final db = await instance.database;
    return await db.query('history', orderBy: 'completedAt DESC');
  }

  Future close() async {
    final db = await instance.database;
    db.close();
  }
}
