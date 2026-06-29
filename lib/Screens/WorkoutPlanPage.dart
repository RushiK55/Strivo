import 'dart:async';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:strivo/Screens/HistoryScreen.dart';
import 'package:strivo/models/Exercise.dart';
import 'package:strivo/models/Plan.dart';
import 'package:strivo/providers/ExerciseProvider.dart';
import 'package:strivo/utils/app_colors.dart';
import '../widgets/wheel_picker.dart';

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
  // Using ValueNotifiers to avoid full-screen rebuilds on every timer tick
  final ValueNotifier<String> _workoutTimeNotifier = ValueNotifier("00:00:00");
  final ValueNotifier<String> _restTimeNotifier = ValueNotifier("00:00:00");
  final ValueNotifier<String> _setTimeNotifier = ValueNotifier("00:00:00");

  late Stopwatch _workoutStopwatch;
  late Stopwatch _restStopwatch;
  late Stopwatch _setStopwatch;
  late Timer _displayTimer;
  
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
      if (_workoutStopwatch.isRunning) {
        _workoutTimeNotifier.value = _formatDuration(_workoutStopwatch.elapsed, includeMs: false);
      }
      if (_restStopwatch.isRunning) {
        _restTimeNotifier.value = _formatDuration(_restStopwatch.elapsed, includeMs: false);
      }
      if (_setStopwatch.isRunning) {
        _setTimeNotifier.value = _formatDuration(_setStopwatch.elapsed, includeMs: false);
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
    _workoutTimeNotifier.dispose();
    _restTimeNotifier.dispose();
    _setTimeNotifier.dispose();
    for (var controller in _textControllers.values) {
      controller.dispose();
    }
    super.dispose();
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
    _workoutStopwatch.reset();
    _workoutTimeNotifier.value = "00:00:00";
    setState(() {});
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

  void _showMetricPicker({
    required String title,
    required int minValue,
    required int maxValue,
    required int initialValue,
    required ValueChanged<int> onSelected,
  }) {
    int tempValue = initialValue;
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.surface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(25)),
        title: Text(title, style: const TextStyle(color: AppColors.textPrimary, fontSize: 18, fontWeight: FontWeight.bold)),
        content: SizedBox(
          height: 180,
          child: WheelPicker(
            label: "",
            minValue: minValue,
            maxValue: maxValue,
            initialValue: initialValue,
            onChanged: (v) => tempValue = v,
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text("CANCEL", style: TextStyle(color: AppColors.textSecondary))),
          TextButton(
            onPressed: () {
              onSelected(tempValue);
              Navigator.pop(context);
            },
            child: const Text("SET", style: TextStyle(color: AppColors.accent, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }

  void _showWeightPicker({
    required double initialWeight,
    required ValueChanged<double> onSelected,
  }) {
    int wInt = initialWeight.floor();
    int wDec = ((initialWeight - wInt) * 10).round();
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.surface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(25)),
        title: const Text("Set Weight", style: TextStyle(color: AppColors.textPrimary, fontSize: 18, fontWeight: FontWeight.bold)),
        content: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          mainAxisSize: MainAxisSize.min,
          children: [
            WheelPicker(
              label: "KG",
              minValue: 0,
              maxValue: 500,
              initialValue: wInt,
              onChanged: (v) => wInt = v,
            ),
            const Padding(
              padding: EdgeInsets.only(top: 40),
              child: Text(".", style: TextStyle(fontSize: 30, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
            ),
            WheelPicker(
              label: "",
              minValue: 0,
              maxValue: 9,
              initialValue: wDec,
              onChanged: (v) => wDec = v,
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text("CANCEL", style: TextStyle(color: AppColors.textSecondary))),
          TextButton(
            onPressed: () {
              onSelected(wInt + (wDec / 10.0));
              Navigator.pop(context);
            },
            child: const Text("SET", style: TextStyle(color: AppColors.accent, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final exerciseProvider = Provider.of<Exerciseprovider>(context);
    final exercises = exerciseProvider.exercises;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: Text(widget.plan.planName,
            style: const TextStyle(fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
        centerTitle: true,
        elevation: 0,
        backgroundColor: AppColors.background,
        foregroundColor: AppColors.textPrimary,
        leading: IconButton(
          icon: Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: AppColors.surface,
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.arrow_back_ios_new, size: 16, color: AppColors.textPrimary),
          ),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: exercises.isEmpty
          ? const Center(child: CircularProgressIndicator(color: AppColors.accent))
          : CustomScrollView(
              physics: const BouncingScrollPhysics(),
              slivers: [
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(24, 20, 24, 24),
                    child: _buildWorkoutTimerHeader(),
                  ),
                ),
                if (_showRestTimer && !_isExerciseRest)
                  SliverToBoxAdapter(
                      child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
                    child: _buildGlobalRestIndicator(),
                  )),
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(24, 0, 24, 40),
                  sliver: SliverList(
                    delegate: SliverChildBuilderDelegate(
                      (context, index) {
                        final exercise = exercises[index];
                        return Column(
                          children: [
                            _buildExerciseListItem(index, exercise),
                            const SizedBox(height: 12),
                            _buildPostExerciseRestIndicator(index, exercise),
                            const SizedBox(height: 12),
                          ],
                        );
                      },
                      childCount: exercises.length,
                    ),
                  ),
                ),
              ],
            ),
    );
  }

  Widget _buildWorkoutTimerHeader() {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(30),
        border: Border.all(color: const Color(0xFF2C2C2E)),
      ),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                padding: const EdgeInsets.all(6),
                decoration: BoxDecoration(
                  color: AppColors.accent.withOpacity(0.1),
                  shape: BoxShape.circle,
                ),
                child: const Icon(Icons.timer_outlined, color: AppColors.accent, size: 16),
              ),
              const SizedBox(width: 8),
              const Text(
                "TOTAL DURATION",
                style: TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                    letterSpacing: 1.5),
              ),
            ],
          ),
          const SizedBox(height: 16),
          ValueListenableBuilder<String>(
            valueListenable: _workoutTimeNotifier,
            builder: (_, value, __) => Text(
              value,
              style: const TextStyle(
                  fontSize: 48,
                  fontWeight: FontWeight.w900,
                  color: AppColors.textPrimary,
                  fontFeatures: [FontFeature.tabularFigures()]),
            ),
          ),
          const SizedBox(height: 24),
          Row(
            children: [
              Expanded(
                child: GestureDetector(
                  onTap: _resetWorkout,
                  child: Container(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    decoration: BoxDecoration(
                      color: const Color(0xFF2C2C2E),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    alignment: Alignment.center,
                    child: const Text("RESET",
                        style: TextStyle(
                            color: AppColors.textPrimary,
                            fontWeight: FontWeight.bold,
                            letterSpacing: 1.2)),
                  ),
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                flex: 2,
                child: GestureDetector(
                  onTap: _startStopWorkout,
                  child: Container(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    decoration: BoxDecoration(
                      color: _workoutStopwatch.isRunning
                          ? Colors.orangeAccent
                          : AppColors.accent,
                      borderRadius: BorderRadius.circular(20),
                      boxShadow: [
                        if (!_workoutStopwatch.isRunning)
                          BoxShadow(
                            color: AppColors.accent.withOpacity(0.2),
                            blurRadius: 15,
                            offset: const Offset(0, 5),
                          )
                      ],
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                            _workoutStopwatch.isRunning
                                ? Icons.pause_rounded
                                : Icons.play_arrow_rounded,
                            color: Colors.black,
                            size: 28),
                        const SizedBox(width: 8),
                        Text(
                          _workoutStopwatch.isRunning ? "PAUSE" : "RESUME",
                          style: const TextStyle(
                              color: Colors.black,
                              fontWeight: FontWeight.w900,
                              fontSize: 16,
                              letterSpacing: 1.2),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
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
        color: Colors.orange.withOpacity(0.1),
        borderRadius: BorderRadius.circular(15),
        border: Border.all(color: Colors.orange.withOpacity(0.3)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.hourglass_top_rounded, color: Colors.orange, size: 20),
          const SizedBox(width: 12),
          ValueListenableBuilder<String>(
            valueListenable: _restTimeNotifier,
            builder: (_, value, __) => Text(
              "SET REST: $value",
              style: const TextStyle(
                  color: Colors.orange,
                  fontWeight: FontWeight.bold,
                  fontSize: 16),
            ),
          ),
          const Spacer(),
          TextButton(
              onPressed: _stopRest,
              child: const Text("SKIP", style: TextStyle(color: Colors.orange))),
        ],
      ),
    );
  }

  Widget _buildPostExerciseRestIndicator(int index, Exercise exercise) {
    final bool isLive =
        _showRestTimer && _isExerciseRest && _activeRestExerciseIndex == index;

    if (isLive) {
      return Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 20),
        decoration: BoxDecoration(
          color: AppColors.accent.withOpacity(0.1),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: AppColors.accent.withOpacity(0.2)),
        ),
        child: Row(
          children: [
            const Icon(Icons.timer_outlined, color: AppColors.accent, size: 20),
            const SizedBox(width: 12),
            const Text(
              "RESTING BEFORE NEXT",
              style: TextStyle(
                  color: AppColors.accent,
                  fontWeight: FontWeight.bold,
                  fontSize: 12),
            ),
            const Spacer(),
            ValueListenableBuilder<String>(
              valueListenable: _restTimeNotifier,
              builder: (_, value, __) => Text(
                value,
                style: const TextStyle(
                    color: AppColors.accent,
                    fontWeight: FontWeight.w900,
                    fontSize: 18),
              ),
            ),
            const SizedBox(width: 16),
            GestureDetector(
              onTap: _stopRest,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.accent,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Text("SKIP",
                    style: TextStyle(
                        color: Colors.black,
                        fontWeight: FontWeight.bold,
                        fontSize: 10)),
              ),
            ),
          ],
        ),
      );
    } else if (exercise.isCheck &&
        exercise.restTime.isNotEmpty &&
        exercise.restTime != "00:00:00") {
      return Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 20),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Row(
          children: [
            const Icon(Icons.history, color: AppColors.textSecondary, size: 20),
            const SizedBox(width: 12),
            const Text(
              "REST TAKEN",
              style: TextStyle(
                  color: AppColors.textSecondary,
                  fontWeight: FontWeight.bold,
                  fontSize: 12),
            ),
            const Spacer(),
            Text(
              exercise.restTime,
              style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontWeight: FontWeight.w900,
                  fontSize: 18),
            ),
          ],
        ),
      );
    }
    return const SizedBox.shrink();
  }

  Widget _buildExerciseListItem(int exerciseIndex, Exercise exercise) {
    _controllers[exerciseIndex] ??= ExpansionTileController();
    final completedSetsCount =
        exercise.setsList.where((s) => s.isCompleted).length;

    return Container(
      key: ValueKey("ex_${exercise.id}"),
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(25),
      ),
      child: ExpansionTile(
        initiallyExpanded: exerciseIndex == widget.initialExerciseIndex,
        controller: _controllers[exerciseIndex],
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(25)),
        collapsedShape:
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(25)),
        iconColor: AppColors.accent,
        collapsedIconColor: AppColors.textSecondary,
        leading: Container(
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: exercise.isCheck
                ? AppColors.accent.withOpacity(0.2)
                : const Color(0xFF2C2C2E),
            shape: BoxShape.circle,
          ),
          child: Icon(
            exercise.isCheck ? Icons.check : Icons.fitness_center,
            color: exercise.isCheck ? AppColors.accent : AppColors.textPrimary,
            size: 20,
          ),
        ),
        title: Text(
          exercise.name,
          style: TextStyle(
            fontWeight: FontWeight.bold,
            fontSize: 18,
            decoration: exercise.isCheck ? TextDecoration.lineThrough : null,
            color: exercise.isCheck
                ? AppColors.textSecondary
                : AppColors.textPrimary,
          ),
        ),
        subtitle: Text(
          "$completedSetsCount / ${exercise.setsList.length} Sets Completed",
          style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
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
                if (!exercise.isCheck &&
                    exercise.setsList.any((s) => s.isCompleted))
                  _buildCompleteExerciseButton(exerciseIndex, exercise),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCompleteExerciseButton(int exerciseIndex, Exercise exercise) {
    return SizedBox(
      width: double.infinity,
      child: ElevatedButton(
        onPressed: () => _showSummaryBottomSheet(exerciseIndex, exercise),
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.accent,
          foregroundColor: Colors.black,
          padding: const EdgeInsets.symmetric(vertical: 14),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(15)),
          elevation: 0,
        ),
        child: const Text("FINISH EXERCISE",
            style: TextStyle(fontWeight: FontWeight.w900, letterSpacing: 1.2)),
      ),
    );
  }

  Widget _buildRestTimeRow(String time, {bool isLive = false}) {
    return Padding(
      padding: const EdgeInsets.only(left: 40, bottom: 12),
      child: Row(
        children: [
          Icon(Icons.hourglass_empty,
              size: 14,
              color: isLive ? Colors.orangeAccent : AppColors.textSecondary),
          const SizedBox(width: 8),
          Text(
            isLive ? "Resting..." : "Rest: $time",
            style: TextStyle(
              fontSize: 12,
              color: isLive ? Colors.orangeAccent : AppColors.textSecondary,
              fontWeight: isLive ? FontWeight.bold : FontWeight.normal,
            ),
          ),
          if (isLive) ...[
            const SizedBox(width: 8),
            ValueListenableBuilder<String>(
              valueListenable: _restTimeNotifier,
              builder: (_, value, __) => Text(
                value,
                style: const TextStyle(
                    fontSize: 12,
                    color: Colors.orangeAccent,
                    fontWeight: FontWeight.bold),
              ),
            ),
          ]
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
        bool isCurrentlyResting = _showRestTimer &&
            _activeRestExerciseIndex == exerciseIndex &&
            _activeRestSetIndex == index;

        return Column(
          children: [
            _buildSetItem(exerciseIndex, exercise, index, set),
            if (isCurrentlyResting)
              _buildRestTimeRow("", isLive: true)
            else if (set.isCompleted &&
                set.restTime.isNotEmpty &&
                set.restTime != "00:00:00")
              _buildRestTimeRow(set.restTime),
          ],
        );
      },
    );
  }

  Widget _buildSetItem(
      int exerciseIndex, Exercise exercise, int index, ExerciseSet set) {
    bool isActive =
        _activeSetExerciseIndex == exerciseIndex && _activeSetIndex == index;
    bool hasDuration =
        set.setDuration.isNotEmpty && set.setDuration != "00:00:00";

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: set.isCompleted
            ? AppColors.accent.withOpacity(0.05)
            : const Color(0xFF2C2C2E),
        borderRadius: BorderRadius.circular(15),
        border: Border.all(
            color: set.isCompleted
                ? AppColors.accent.withOpacity(0.2)
                : Colors.transparent),
      ),
      child: Column(
        children: [
          Row(
            children: [
              CircleAvatar(
                radius: 12,
                backgroundColor:
                    set.isCompleted ? AppColors.accent : AppColors.surface,
                child: Text("${index + 1}",
                    style: TextStyle(
                        fontSize: 10,
                        color: set.isCompleted ? Colors.black : AppColors.textPrimary,
                        fontWeight: FontWeight.bold)),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: GestureDetector(
                  onTap: () => _showWeightPicker(
                    initialWeight: set.weight,
                    onSelected: (val) {
                      setState(() => set.weight = val);
                      _saveExercise(exercise);
                    },
                  ),
                  child: Container(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    decoration: const BoxDecoration(
                      border: Border(bottom: BorderSide(color: Color(0xFF3A3A3C))),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text("KG", style: TextStyle(color: AppColors.textSecondary, fontSize: 10)),
                        Text(set.weight == 0 ? "---" : set.weight.toString(),
                            style: const TextStyle(color: AppColors.textPrimary, fontSize: 16, fontWeight: FontWeight.bold)),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: GestureDetector(
                  onTap: () => _showMetricPicker(
                    title: "Set Reps",
                    minValue: 1,
                    maxValue: 100,
                    initialValue: set.reps == 0 ? 10 : set.reps,
                    onSelected: (val) {
                      setState(() => set.reps = val);
                      _saveExercise(exercise);
                    },
                  ),
                  child: Container(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    decoration: const BoxDecoration(
                      border: Border(bottom: BorderSide(color: Color(0xFF3A3A3C))),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text("REPS", style: TextStyle(color: AppColors.textSecondary, fontSize: 10)),
                        Text(set.reps == 0 ? "---" : set.reps.toString(),
                            style: const TextStyle(color: AppColors.textPrimary, fontSize: 16, fontWeight: FontWeight.bold)),
                      ],
                    ),
                  ),
                ),
              ),
              if (!set.isCompleted)
                IconButton(
                  onPressed: () => _deleteSet(exercise, index),
                  icon: const Icon(Icons.remove_circle_outline,
                      color: Colors.redAccent, size: 20),
                  tooltip: "Delete Set",
                ),
              IconButton(
                onPressed: () {
                  if (set.isCompleted) {
                    setState(() {
                      set.isCompleted = false;
                      if (index == exercise.setsList.length - 1) {
                        exercise.isCheck = false;
                      }
                      _stopRest();
                    });
                  } else if (isActive) {
                    setState(() {
                      set.isCompleted = true;
                      set.isStarted = false;
                      set.setDuration =
                          _formatDuration(_setStopwatch.elapsed, includeMs: false);
                      _setStopwatch.stop();
                      _activeSetIndex = null;
                      _activeSetExerciseIndex = null;

                      if (index < exercise.setsList.length - 1) {
                        _triggerRest(exerciseIndex, index,
                            isExerciseRest: false);
                      } else {
                        exercise.isCheck = true;
                        _triggerRest(exerciseIndex, index,
                            isExerciseRest: true);

                        _controllers[exerciseIndex]?.collapse();
                        final provider = Provider.of<Exerciseprovider>(context,
                            listen: false);
                        int nextIndex = -1;
                        for (int i = 0; i < provider.exercises.length; i++) {
                          if (!provider.exercises[i].isCheck) {
                            nextIndex = i;
                            break;
                          }
                        }

                        if (nextIndex != -1) {
                          _controllers[nextIndex]?.expand();
                        } else {
                          _workoutStopwatch.stop();
                          provider.saveCompletedWorkout(
                              widget.plan.planName, _workoutTimeNotifier.value);
                          _showWorkoutCompleteBottomSheet();
                        }
                      }
                    });
                  } else {
                    _startSet(exerciseIndex, index);
                  }
                  _saveExercise(exercise);
                },
                icon: Icon(
                    set.isCompleted
                        ? Icons.check_circle
                        : (isActive
                            ? Icons.pause_circle_filled
                            : Icons.play_circle_outline),
                    color: set.isCompleted
                        ? AppColors.accent
                        : (isActive
                            ? Colors.orangeAccent
                            : AppColors.textSecondary)),
              ),
            ],
          ),
          if (isActive || hasDuration)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  Icon(isActive ? Icons.timer : Icons.history_toggle_off,
                      size: 12,
                      color: isActive ? Colors.orangeAccent : AppColors.textSecondary),
                  const SizedBox(width: 4),
                  isActive
                      ? ValueListenableBuilder<String>(
                          valueListenable: _setTimeNotifier,
                          builder: (_, value, __) => Text(
                            "Live: $value",
                            style: const TextStyle(
                                fontSize: 10,
                                color: Colors.orangeAccent,
                                fontWeight: FontWeight.bold),
                          ),
                        )
                      : Text(
                          "Took: ${set.setDuration}",
                          style: const TextStyle(
                              fontSize: 10,
                              color: AppColors.textSecondary,
                              fontWeight: FontWeight.bold),
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
      icon: const Icon(Icons.add, size: 18, color: AppColors.accent),
      label: const Text("Add Set", style: TextStyle(color: AppColors.accent)),
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
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (context) => Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text("Complete ${exercise.name}?",
                style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                    color: AppColors.textPrimary)),
            const SizedBox(height: 12),
            ValueListenableBuilder<String>(
              valueListenable: _workoutTimeNotifier,
              builder: (_, value, __) => Text("Workout duration: $value",
                  style: const TextStyle(color: AppColors.textSecondary)),
            ),
            const SizedBox(height: 32),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () {
                      Navigator.pop(context);
                      _addSet(exercise);
                    },
                    icon: const Icon(Icons.add, color: AppColors.accent),
                    label: const Text("ADD SET",
                        style: TextStyle(
                            color: AppColors.accent,
                            fontWeight: FontWeight.bold)),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      side: const BorderSide(color: AppColors.accent),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(15)),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  flex: 2,
                  child: ElevatedButton(
                    onPressed: () async {
                      _stopRest();

                      final provider = Provider.of<Exerciseprovider>(context,
                          listen: false);

                      bool isLastExercise = provider.exercises
                          .every((e) => e.isCheck || e.id == exercise.id);

                      setState(() {
                        exercise.isCheck = true;
                        _controllers[exerciseIndex]?.collapse();

                        bool hasNextExercise = false;
                        for (int i = 0; i < provider.exercises.length; i++) {
                          if (!provider.exercises[i].isCheck &&
                              i != exerciseIndex) {
                            _controllers[i]?.expand();
                            hasNextExercise = true;
                            break;
                          }
                        }

                        if (!hasNextExercise) {
                          _workoutStopwatch.stop();
                        }

                        _triggerRest(exerciseIndex, 999,
                            isExerciseRest: true);
                      });
                      _saveExercise(exercise);
                      Navigator.pop(context);

                      if (isLastExercise) {
                        await provider.saveCompletedWorkout(
                            widget.plan.planName, _workoutTimeNotifier.value);
                        if (mounted) {
                          _showWorkoutCompleteBottomSheet();
                        }
                      }
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.accent,
                      foregroundColor: Colors.black,
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(15)),
                      elevation: 0,
                    ),
                    child: const Text("CONFIRM & SAVE",
                        style: TextStyle(fontWeight: FontWeight.bold)),
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
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(30))),
      builder: (context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppColors.accent.withOpacity(0.1),
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.emoji_events_outlined,
                  color: AppColors.accent, size: 48),
            ),
            const SizedBox(height: 24),
            const Text(
              "WORKOUT COMPLETE!",
              style: TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.w900,
                  color: AppColors.textPrimary),
            ),
            const SizedBox(height: 8),
            Text(
              "You've smashed your '${widget.plan.planName}' session.",
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.textSecondary, fontSize: 16),
            ),
            const SizedBox(height: 32),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _buildStatItem("Duration", _workoutTimeNotifier.value,
                    Icons.timer_outlined),
                _buildStatItem(
                    "Exercises",
                    Provider.of<Exerciseprovider>(context, listen: false)
                        .exercises
                        .length
                        .toString(),
                    Icons.fitness_center),
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
                    MaterialPageRoute(
                        builder: (context) => const HistoryScreen()),
                    (route) => route.isFirst,
                  );
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.accent,
                  foregroundColor: Colors.black,
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(20)),
                  elevation: 0,
                ),
                child: const Text("FINISH WORKOUT",
                    style:
                        TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
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
        Icon(icon, color: AppColors.accent, size: 24),
        const SizedBox(height: 8),
        Text(value,
            style: const TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.w900,
                color: AppColors.textPrimary)),
        Text(label,
            style: const TextStyle(color: AppColors.textSecondary, fontSize: 12)),
      ],
    );
  }
}
