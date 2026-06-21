import 'dart:async';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:strivo/Screens/HistoryScreen.dart';
import 'package:strivo/models/Exercise.dart';
import 'package:strivo/models/Plan.dart';
import 'package:strivo/providers/ExerciseProvider.dart';

class WorkoutPlanPage extends StatefulWidget {
  final Plan plan;
  final int initialExerciseIndex;

  const WorkoutPlanPage({
    super.key,
    required this.plan,
    this.initialExerciseIndex = 0,
  });

  @override
  State<WorkoutPlanPage> createState() => _WorkoutPlanPageState();
}

class _WorkoutPlanPageState extends State<WorkoutPlanPage> {
  late Stopwatch _workoutStopwatch;
  late Stopwatch _restStopwatch;
  late Stopwatch _setStopwatch;
  late Timer _displayTimer;
  
  String _workoutTime = "00:00:00";
  String _restTime = "00:00:00";
  String _setTime = "00:00:00";
  bool _showRestTimer = false;
  bool _isExerciseRest = false;
  
  // Using Controllers for smooth expansion without rebuilds
  final Map<int, ExpansionTileController> _controllers = {};
  final Map<String, TextEditingController> _textControllers = {};
  Timer? _saveDebounce;
  int? _activeSetExerciseIndex;
  int? _activeSetIndex;
  int? _activeRestExerciseIndex;
  int? _activeRestSetIndex;

  @override
  void initState() {
    super.initState();
    _workoutStopwatch = Stopwatch();
    _restStopwatch = Stopwatch();
    _setStopwatch = Stopwatch();
    
    _displayTimer = Timer.periodic(const Duration(milliseconds: 100), (timer) {
      if (mounted) {
        setState(() {
          if (_workoutStopwatch.isRunning) {
            _workoutTime = _formatDuration(_workoutStopwatch.elapsed, includeMs: false);
          }
          if (_restStopwatch.isRunning) {
            _restTime = _formatDuration(_restStopwatch.elapsed, includeMs: false);
          }
          if (_setStopwatch.isRunning) {
            _setTime = _formatDuration(_setStopwatch.elapsed, includeMs: false);
          }
        });
      }
    });

    WidgetsBinding.instance.addPostFrameCallback((_) {
      final provider = Provider.of<Exerciseprovider>(context, listen: false);
      for (var exercise in provider.exercises) {
        if (exercise.setsList.isEmpty) {
          exercise.setsList.add(ExerciseSet(weight: 0, reps: 0));
        }
      }
    });
  }

  @override
  void dispose() {
    _displayTimer.cancel();
    _saveDebounce?.cancel();
    for (var controller in _textControllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  TextEditingController _getController(Exercise exercise, int setIndex, String type, String initialValue) {
    final key = "${exercise.id}_${setIndex}_$type";
    if (!_textControllers.containsKey(key)) {
      _textControllers[key] = TextEditingController(text: initialValue);
    }
    return _textControllers[key]!;
  }

  void _saveExerciseDebounced(Exercise exercise) {
    if (_saveDebounce?.isActive ?? false) _saveDebounce!.cancel();
    _saveDebounce = Timer(const Duration(milliseconds: 1000), () {
      _saveExercise(exercise);
    });
  }

  String _formatDuration(Duration duration, {required bool includeMs}) {
    String twoDigits(int n) => n.toString().padLeft(2, "0");
    String hours = twoDigits(duration.inHours);
    String minutes = twoDigits(duration.inMinutes.remainder(60));
    String seconds = twoDigits(duration.inSeconds.remainder(60));
    if (includeMs) {
      String milliSeconds = (duration.inMilliseconds.remainder(1000) ~/ 100).toString();
      return "$hours:$minutes:$seconds.$milliSeconds";
    }
    return "$hours:$minutes:$seconds";
  }

  void _startStopWorkout() {
    if (!_workoutStopwatch.isRunning) {
      final provider = Provider.of<Exerciseprovider>(context, listen: false);
      bool hasValidSet = provider.exercises.any((ex) => ex.setsList.any((s) => s.weight > 0 && s.reps > 0));
      if (!hasValidSet) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text("Please enter weight and reps for at least one set"),
            backgroundColor: Colors.orange,
          ),
        );
        return;
      }
    }
    setState(() {
      if (_workoutStopwatch.isRunning) {
        _workoutStopwatch.stop();
      } else {
        _workoutStopwatch.start();
      }
    });
  }

  void _resetWorkout() {
    setState(() {
      _workoutStopwatch.reset();
      _workoutTime = "00:00:00";
    });
  }

  void _triggerRest(int exerciseIndex, int setIndex, {bool isExerciseRest = false}) {
    setState(() {
      _activeRestExerciseIndex = exerciseIndex;
      _activeRestSetIndex = setIndex;
      _restStopwatch.reset();
      _restStopwatch.start();
      _showRestTimer = true;
      _isExerciseRest = isExerciseRest;
    });
  }

  void _stopRest() {
    final provider = Provider.of<Exerciseprovider>(context, listen: false);
    if (_activeRestExerciseIndex != null && _activeRestSetIndex != null) {
      final exercise = provider.exercises[_activeRestExerciseIndex!];
      if (_isExerciseRest) {
        exercise.restTime = _formatDuration(_restStopwatch.elapsed, includeMs: false);
      } else if (_activeRestSetIndex! < exercise.setsList.length) {
        exercise.setsList[_activeRestSetIndex!].restTime = _formatDuration(_restStopwatch.elapsed, includeMs: false);
      }
      _saveExercise(exercise);
    }
    setState(() {
      _restStopwatch.stop();
      _showRestTimer = false;
      _activeRestExerciseIndex = null;
      _activeRestSetIndex = null;
      _isExerciseRest = false;
    });
  }

  void _startSet(int exerciseIndex, int setIndex) {
    if (_activeSetIndex != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text("Please complete the current set before starting another one"),
          backgroundColor: Colors.orange,
        ),
      );
      return;
    }

    final provider = Provider.of<Exerciseprovider>(context, listen: false);
    final exercise = provider.exercises[exerciseIndex];
    final set = exercise.setsList[setIndex];
    
    if (set.weight <= 0 || set.reps <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text("Please enter Weight and Reps"),
          backgroundColor: Colors.redAccent,
        ),
      );
      return;
    }

    setState(() {
      _activeSetExerciseIndex = exerciseIndex;
      _activeSetIndex = setIndex;
      _setStopwatch.reset();
      _setStopwatch.start();
      set.isStarted = true;
      _stopRest();
      if (!_workoutStopwatch.isRunning) {
        _workoutStopwatch.start();
      }
    });
    _saveExercise(exercise);
  }

  void _addSet(Exercise exercise) {
    setState(() {
      double lastWeight = 0;
      int lastReps = 0;
      if (exercise.setsList.isNotEmpty) {
        lastWeight = exercise.setsList.last.weight;
        lastReps = exercise.setsList.last.reps;
      }
      exercise.setsList.add(ExerciseSet(weight: lastWeight, reps: lastReps));
    });
    _saveExercise(exercise);
  }

  void _deleteSet(Exercise exercise, int index) {
    if (exercise.setsList[index].isCompleted || exercise.setsList[index].isStarted) return;
    setState(() {
      exercise.setsList.removeAt(index);
      _textControllers.removeWhere((key, value) => key.startsWith("${exercise.id}_"));
    });
    _saveExercise(exercise);
  }

  void _saveExercise(Exercise exercise) {
    if (exercise.setsList.isNotEmpty) {
      exercise.sets = exercise.setsList.length.toString();
      double lastWeight = exercise.setsList.lastWhere((s) => s.weight > 0, orElse: () => exercise.setsList.first).weight;
      exercise.weight = lastWeight.toString();
      int lastReps = exercise.setsList.lastWhere((s) => s.reps > 0, orElse: () => exercise.setsList.first).reps;
      exercise.reps = lastReps.toString();
    }
    Provider.of<Exerciseprovider>(context, listen: false).updateExercise(exercise);
  }

  @override
  Widget build(BuildContext context) {
    final exerciseProvider = Provider.of<Exerciseprovider>(context);
    final exercises = exerciseProvider.exercises;

    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FE),
      appBar: AppBar(
        title: Text(widget.plan.planName, style: const TextStyle(fontWeight: FontWeight.bold)),
        elevation: 0,
        backgroundColor: Colors.white,
        foregroundColor: Colors.black,
      ),
      body: exercises.isEmpty
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: _buildWorkoutTimer(),
                ),
                if (_showRestTimer && !_isExerciseRest) _buildGlobalRestIndicator(),
                Expanded(
                  child: ListView.builder(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 20),
                    itemCount: exercises.length,
                    itemBuilder: (context, index) {
                      final exercise = exercises[index];
                      return Column(
                        children: [
                          _buildExerciseListItem(index, exercise),
                          _buildPostExerciseRestIndicator(index, exercise),
                        ],
                      );
                    },
                  ),
                ),
              ],
            ),
    );
  }

  Widget _buildGlobalRestIndicator() {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: _isExerciseRest ? Colors.green.withValues(alpha: 0.1) : Colors.orange.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: _isExerciseRest ? Colors.green.withValues(alpha: 0.3) : Colors.orange.withValues(alpha: 0.3)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.hourglass_top_rounded, color: _isExerciseRest ? Colors.green : Colors.orange, size: 20),
          const SizedBox(width: 12),
          Text(
            _isExerciseRest ? "EXERCISE REST: $_restTime" : "SET REST: $_restTime",
            style: TextStyle(color: _isExerciseRest ? Colors.green : Colors.orange, fontWeight: FontWeight.bold, fontSize: 16),
          ),
          const Spacer(),
          TextButton(onPressed: _stopRest, child: const Text("SKIP")),
        ],
      ),
    );
  }

  Widget _buildPostExerciseRestIndicator(int index, Exercise exercise) {
    final bool isLive = _showRestTimer && _isExerciseRest && _activeRestExerciseIndex == index;
    final String displayTime = isLive ? _restTime : exercise.restTime;

    if (displayTime.isEmpty || displayTime == "00:00:00") return const SizedBox.shrink();

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 20),
      decoration: BoxDecoration(
        color: isLive ? Colors.green.withValues(alpha: 0.1) : Colors.grey.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(15),
        border: Border.all(color: isLive ? Colors.green.withValues(alpha: 0.2) : Colors.grey.withValues(alpha: 0.1)),
      ),
      child: Row(
        children: [
          Icon(isLive ? Icons.timer_outlined : Icons.history, color: isLive ? Colors.green : Colors.grey, size: 20),
          const SizedBox(width: 12),
          Text(
            isLive ? "RESTING BEFORE NEXT" : "REST TAKEN",
            style: TextStyle(color: isLive ? Colors.green : Colors.grey[700], fontWeight: FontWeight.bold, fontSize: 12),
          ),
          const Spacer(),
          Text(
            displayTime,
            style: TextStyle(color: isLive ? Colors.green : Colors.grey[800], fontWeight: FontWeight.w900, fontSize: 18),
          ),
          if (isLive) ...[
            const SizedBox(width: 16),
            GestureDetector(
              onTap: _stopRest,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.green,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Text("SKIP", style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 10)),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildExerciseListItem(int exerciseIndex, Exercise exercise) {
    _controllers[exerciseIndex] ??= ExpansionTileController();
    final completedSetsCount = exercise.setsList.where((s) => s.isCompleted).length;
    
    return Container(
      key: ValueKey("ex_${exercise.id}"), // Stable key for focus retention
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: Colors.grey.withValues(alpha: 0.1)),
        boxShadow: [
          BoxShadow(color: Colors.black.withValues(alpha: 0.03), blurRadius: 10, offset: const Offset(0, 4)),
        ],
      ),
      child: ExpansionTile(
        initiallyExpanded: exerciseIndex == widget.initialExerciseIndex,
        controller: _controllers[exerciseIndex],
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(20))),
        collapsedShape: const RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(20))),
        leading: Icon(
          exercise.isCheck ? Icons.check_circle : Icons.fitness_center,
          color: exercise.isCheck ? Colors.green : Colors.deepPurple,
        ),
        title: Text(
          exercise.name,
          style: TextStyle(
            fontWeight: FontWeight.bold,
            decoration: exercise.isCheck ? TextDecoration.lineThrough : null,
            color: exercise.isCheck ? Colors.grey : Colors.black87,
          ),
        ),
          subtitle: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                "$completedSetsCount / ${exercise.setsList.length} Sets Completed",
                style: TextStyle(fontSize: 12, color: Colors.grey[600]),
              ),
              if (exercise.isCheck && ((_showRestTimer && _isExerciseRest && _activeRestExerciseIndex == exerciseIndex) || (exercise.restTime.isNotEmpty && exercise.restTime != "00:00:00")))
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Row(
                    children: [
                      Icon(
                        _isExerciseRest && _activeRestExerciseIndex == exerciseIndex ? Icons.hourglass_top_rounded : Icons.hotel_class_outlined,
                        size: 12,
                        color: Colors.green,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        _isExerciseRest && _activeRestExerciseIndex == exerciseIndex 
                          ? "Current Rest: $_restTime"
                          : "Post-Exercise Rest: ${exercise.restTime}",
                        style: const TextStyle(fontSize: 11, color: Colors.green, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (!exercise.isCheck && completedSetsCount > 0)
            const Icon(Icons.expand_more),
          ],
        ),
        children: [
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              children: [
                _buildSetsList(exerciseIndex, exercise),
                const SizedBox(height: 12),
                _buildAddSetButton(exercise),
                const SizedBox(height: 16),
                if (!exercise.isCheck && exercise.setsList.any((s) => s.isCompleted))
                  _buildCompleteExerciseButton(exerciseIndex, exercise),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildWorkoutTimer() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(24),
        boxShadow: [
          BoxShadow(color: Colors.black.withValues(alpha: 0.04), blurRadius: 12, offset: const Offset(0, 4)),
        ],
      ),
      child: Column(
        children: [
          Text(
            "TOTAL WORKOUT TIME",
            style: TextStyle(color: Colors.grey[600], fontSize: 11, fontWeight: FontWeight.bold, letterSpacing: 1.2),
          ),
          const SizedBox(height: 8),
          Text(
            _workoutTime,
            style: const TextStyle(fontSize: 36, fontWeight: FontWeight.w900, fontFeatures: [FontFeature.tabularFigures()]),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: _resetWorkout,
                  style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    side: BorderSide(color: Colors.grey[200]!),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                  child: const Text("RESET", style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold)),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                flex: 2,
                child: ElevatedButton.icon(
                  onPressed: _startStopWorkout,
                  icon: Icon(_workoutStopwatch.isRunning ? Icons.pause : Icons.play_arrow),
                  label: Text(_workoutStopwatch.isRunning ? "PAUSE" : "START"),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: _workoutStopwatch.isRunning ? Colors.orange : const Color(0xFFD0FD3E),
                    foregroundColor: Colors.black,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                    elevation: 0,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildSetsList(int exerciseIndex, Exercise exercise) {
    return ListView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: exercise.setsList.length,
      itemBuilder: (context, index) {
        final set = exercise.setsList[index];
        bool isCurrentlyResting = _showRestTimer && _activeRestExerciseIndex == exerciseIndex && _activeRestSetIndex == index;
        
        return Column(
          children: [
            _buildSetItem(exerciseIndex, exercise, index, set),
            if (isCurrentlyResting)
              _buildRestTimeRow(_restTime, isLive: true)
            else if (set.restTime.isNotEmpty && set.restTime != "00:00:00")
               _buildRestTimeRow(set.restTime),
          ],
        );
      },
    );
  }

  Widget _buildRestTimeRow(String restTime, {bool isLive = false}) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
      decoration: BoxDecoration(
        color: isLive ? Colors.orange.withValues(alpha: 0.1) : Colors.deepPurple.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(12),
        border: isLive ? Border.all(color: Colors.orange.withValues(alpha: 0.3)) : null,
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            isLive ? Icons.hourglass_top_rounded : Icons.timer_outlined, 
            size: 14, 
            color: isLive ? Colors.orange : Colors.deepPurple
          ),
          const SizedBox(width: 8),
          Text(
            isLive ? "RESTING: $restTime" : "REST: $restTime",
            style: TextStyle(
              color: isLive ? Colors.orange.shade800 : Colors.deepPurple,
              fontSize: 12,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSetItem(int exerciseIndex, Exercise exercise, int index, ExerciseSet set) {
    bool isActive = _activeSetExerciseIndex == exerciseIndex && _activeSetIndex == index;
    bool hasDuration = set.setDuration.isNotEmpty && set.setDuration != "00:00:00";

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: set.isCompleted ? Colors.green.withValues(alpha: 0.05) : Colors.grey[50],
        borderRadius: BorderRadius.circular(15),
        border: Border.all(color: set.isCompleted ? Colors.green.withValues(alpha: 0.2) : Colors.transparent),
      ),
      child: Column(
        children: [
          Row(
            children: [
              CircleAvatar(
                radius: 12,
                backgroundColor: set.isCompleted ? Colors.green : Colors.grey[300],
                child: Text("${index + 1}", style: const TextStyle(fontSize: 10, color: Colors.white, fontWeight: FontWeight.bold)),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: TextFormField(
                  controller: _getController(exercise, index, "weight", set.weight == 0 ? "" : set.weight.toString()),
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: "KG", isDense: true, border: InputBorder.none),
                  onChanged: (val) {
                    set.weight = double.tryParse(val) ?? 0;
                    _saveExerciseDebounced(exercise);
                  },
                ),
              ),
              Expanded(
                child: TextFormField(
                  controller: _getController(exercise, index, "reps", set.reps == 0 ? "" : set.reps.toString()),
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: "REPS", isDense: true, border: InputBorder.none),
                  onChanged: (val) {
                    set.reps = int.tryParse(val) ?? 0;
                    _saveExerciseDebounced(exercise);
                  },
                ),
              ),
              if (!set.isCompleted && !set.isStarted)
                IconButton(
                  onPressed: () => _deleteSet(exercise, index),
                  icon: const Icon(Icons.remove_circle_outline, color: Colors.redAccent, size: 20),
                  tooltip: "Delete Set",
                ),
              if (!set.isStarted)
                IconButton(onPressed: () => _startSet(exerciseIndex, index), icon: const Icon(Icons.play_arrow, color: Colors.green))
              else
                IconButton(
                  onPressed: () {
                    setState(() {
                      set.isCompleted = !set.isCompleted;
                      if (set.isCompleted) {
                        if (isActive) {
                          set.setDuration = _formatDuration(_setStopwatch.elapsed, includeMs: false);
                          _setStopwatch.stop();
                          _activeSetExerciseIndex = null;
                          _activeSetIndex = null;
                        }

                        // Always trigger rest after a set, even if it's the last one
                        _triggerRest(exerciseIndex, index);

                        if (exercise.setsList.every((s) => s.isCompleted)) {
                          _showSummaryBottomSheet(exerciseIndex, exercise);
                        }
                      } else {
                        _stopRest();
                      }
                    });
                    _saveExercise(exercise);
                  },
                  icon: Icon(set.isCompleted ? Icons.check_circle : Icons.radio_button_unchecked, color: set.isCompleted ? Colors.green : Colors.grey),
                ),
            ],
          ),
          if (isActive || hasDuration)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  Icon(isActive ? Icons.timer : Icons.history_toggle_off, size: 12, color: isActive ? Colors.orange : Colors.grey),
                  const SizedBox(width: 4),
                  Text(
                    isActive ? "Live: $_setTime" : "Took: ${set.setDuration}",
                    style: TextStyle(fontSize: 10, color: isActive ? Colors.orange : Colors.grey, fontWeight: FontWeight.bold),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildAddSetButton(Exercise exercise) {
    return TextButton.icon(
      onPressed: () => _addSet(exercise),
      icon: const Icon(Icons.add, size: 18),
      label: const Text("Add Set"),
    );
  }

  Widget _buildCompleteExerciseButton(int exerciseIndex, Exercise exercise) {
    return SizedBox(
      width: double.infinity,
      child: ElevatedButton(
        onPressed: () => _showSummaryBottomSheet(exerciseIndex, exercise),
        style: ElevatedButton.styleFrom(
          backgroundColor: const Color(0xFFD0FD3E),
          foregroundColor: Colors.black,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
        child: const Text("COMPLETE EXERCISE", style: TextStyle(fontWeight: FontWeight.bold)),
      ),
    );
  }

  void _showSummaryBottomSheet(int exerciseIndex, Exercise exercise) {
    final hasUncompletedSets = exercise.setsList.any((s) => !s.isCompleted);
    if (hasUncompletedSets) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text("Please complete all sets before finishing the exercise"),
          backgroundColor: Colors.redAccent,
        ),
      );
      return;
    }

    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (context) => Padding(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text("Complete ${exercise.name}?", style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 10),
            Text("Workout duration: $_workoutTime", style: TextStyle(color: Colors.grey[600])),
            const SizedBox(height: 24),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () {
                      Navigator.pop(context);
                      _addSet(exercise);
                    },
                    icon: const Icon(Icons.add, color: Colors.deepPurple),
                    label: const Text("ADD SET", style: TextStyle(color: Colors.deepPurple, fontWeight: FontWeight.bold)),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      side: const BorderSide(color: Colors.deepPurple),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  flex: 2,
                  child: ElevatedButton(
                    onPressed: () {
                      _stopRest(); // Stop any set rest before starting exercise rest
                      setState(() {
                        exercise.isCheck = true;
                        _controllers[exerciseIndex]?.collapse();
                        
                        // Auto-maximize next exercise
                        final provider = Provider.of<Exerciseprovider>(context, listen: false);
                        bool hasNextExercise = false;
                        for (int i = 0; i < provider.exercises.length; i++) {
                          if (!provider.exercises[i].isCheck && i != exerciseIndex) {
                            _controllers[i]?.expand();
                            hasNextExercise = true;
                            break;
                          }
                        }

                        if (!hasNextExercise) {
                          // This was the last exercise
                          _workoutStopwatch.stop();
                        }

                        // Start exercise-level rest
                        _triggerRest(exerciseIndex, 999, isExerciseRest: true); 
                      });
                      _saveExercise(exercise);
                      Navigator.pop(context);

                      // Check for workout completion
                      final provider = Provider.of<Exerciseprovider>(context, listen: false);
                      bool allFinished = provider.exercises.every((e) => e.isCheck || e.id == exercise.id);
                      if (allFinished) {
                        Future.delayed(const Duration(milliseconds: 500), () {
                          if (mounted) {
                            _showWorkoutCompleteBottomSheet();
                          }
                        });
                      }
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFFD0FD3E),
                      foregroundColor: Colors.black,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      elevation: 0,
                    ),
                    child: const Text("CONFIRM & SAVE", style: TextStyle(fontWeight: FontWeight.bold)),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
          ],
        ),
      ),
    );
  }
  void _showWorkoutCompleteBottomSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(30))),
      builder: (context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: const Color(0xFFD0FD3E).withValues(alpha: 0.1),
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.emoji_events_outlined, color: Colors.black, size: 48),
            ),
            const SizedBox(height: 24),
            const Text(
              "WORKOUT COMPLETE!",
              style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900),
            ),
            const SizedBox(height: 8),
            Text(
              "You've smashed your '${widget.plan.planName}' session.",
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.grey[600], fontSize: 16),
            ),
            const SizedBox(height: 32),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _buildStatItem("Duration", _workoutTime, Icons.timer_outlined),
                _buildStatItem("Exercises", Provider.of<Exerciseprovider>(context, listen: false).exercises.length.toString(), Icons.fitness_center),
              ],
            ),
            const SizedBox(height: 40),
            SizedBox(
              width: double.infinity,
              height: 56,
              child: ElevatedButton(
                onPressed: () {
                  Navigator.pop(context); // Close sheet
                  Navigator.pushAndRemoveUntil(
                    context,
                    MaterialPageRoute(builder: (context) => const HistoryScreen()),
                    (route) => route.isFirst,
                  );
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFFD0FD3E),
                  foregroundColor: Colors.black,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  elevation: 0,
                ),
                child: const Text("FINISH WORKOUT", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatItem(String label, String value, IconData icon) {
    return Column(
      children: [
        Icon(icon, color: Colors.deepPurple, size: 24),
        const SizedBox(height: 8),
        Text(value, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
        Text(label, style: TextStyle(color: Colors.grey[600], fontSize: 12)),
      ],
    );
  }
}
