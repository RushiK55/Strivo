import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:strivo/Screens/WorkoutPlanPage.dart';
import 'package:strivo/Screens/ExerciseDetailsScreen.dart';
import 'package:strivo/Screens/DayPlansScreen.dart';
import 'package:strivo/Screens/save_plan.dart';
import 'package:strivo/Screens/SaveExercise.dart';
import 'package:strivo/Screens/ProfileScreen.dart';
import 'package:strivo/providers/ExerciseProvider.dart';
import 'package:strivo/providers/PlanProvider.dart';
import 'package:strivo/models/Plan.dart';
import 'package:strivo/providers/AuthProvider.dart';
import 'package:strivo/services/user_manager.dart';
import 'package:strivo/widgets/wheel_picker.dart';

class Homescreen extends StatefulWidget {
  const Homescreen({super.key});

  @override
  State<Homescreen> createState() => _HomescreenState();
}

class _HomescreenState extends State<Homescreen> {
  int? _selectedPlanId; // null means "All", -1 means "Extra"
  late String _selectedDay;
  late List<DateTime> _currentWeek;
  
  final List<String> _allDays = [
    'Monday',
    'Tuesday',
    'Wednesday',
    'Thursday',
    'Friday',
    'Saturday',
    'Sunday'
  ];

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _selectedDay = _getDayName(now.weekday);
    _currentWeek = _generateCurrentWeek(now);
    
    WidgetsBinding.instance.addPostFrameCallback((_) {
      Provider.of<Planprovider>(context, listen: false).refreshPlans();
      Provider.of<Exerciseprovider>(context, listen: false).loadExercisesByDay(_selectedDay);
      _checkWeeklyWeight();
    });
  }

  List<DateTime> _generateCurrentWeek(DateTime date) {
    int currentWeekday = date.weekday; // 1 = Monday, 7 = Sunday
    DateTime monday = date.subtract(Duration(days: currentWeekday - 1));
    return List.generate(7, (index) => monday.add(Duration(days: index)));
  }

  Future<void> _checkWeeklyWeight() async {
    bool shouldAsk = await UserManager.shouldAskWeight();
    if (shouldAsk && mounted) {
      _showWeightUpdateDialog();
    }
  }

  void _showWeightUpdateDialog() {
    int wInt = 70;
    int wDec = 0;
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        title: const Text("Weekly Weight Update"),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text("It's been a week! Let's update your current weight to track progress."),
            const SizedBox(height: 20),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                WheelPicker(label: "KG", minValue: 30, maxValue: 250, initialValue: wInt, onChanged: (v) => wInt = v),
                const Text(".", style: TextStyle(fontSize: 30, fontWeight: FontWeight.bold)),
                WheelPicker(label: "", minValue: 0, maxValue: 9, initialValue: wDec, onChanged: (v) => wDec = v),
              ],
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () async {
              await UserManager.updateWeightOnly(wInt + (wDec / 10.0));
              if (context.mounted) Navigator.pop(context);
            },
            child: const Text("Update"),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final exerciseProvider = Provider.of<Exerciseprovider>(context);
    final planProvider = Provider.of<Planprovider>(context);
    final authProvider = Provider.of<AuthProvider>(context);
    
    final dayName = _selectedDay;
    
    final groupedPlans = planProvider.plansByDay;

    return Scaffold(
      body: CustomScrollView(
        physics: const BouncingScrollPhysics(),
        slivers: [
          SliverAppBar(
            expandedHeight: 200.0,
            floating: false,
            pinned: true,
            actions: [
              IconButton(
                icon: const Icon(Icons.account_circle, color: Colors.white, size: 30),
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(builder: (context) => const ProfileScreen()),
                  );
                },
              ),
              const SizedBox(width: 10),
            ],
            flexibleSpace: FlexibleSpaceBar(
              title: Text(
                "Hello, ${authProvider.userName?.split(' ')[0] ?? 'User'}",
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.bold,
                ),
              ),
              background: Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [Colors.deepPurple, Colors.deepPurple.shade800],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                ),
                child: Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.fitness_center, size: 48, color: Colors.white.withOpacity(0.5)),
                      const SizedBox(height: 10),
                      const Text(
                        "Strivo",
                        style: TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold, letterSpacing: 2),
                      ),
                      Text(
                        _getDayName(DateTime.now().weekday),
                        style: const TextStyle(color: Colors.white70, fontSize: 16, fontWeight: FontWeight.w300),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          
          // Weekly Schedule Selector
          SliverToBoxAdapter(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Padding(
                  padding: EdgeInsets.fromLTRB(20, 25, 20, 10),
                  child: Text(
                    "Weekly Schedule",
                    style: TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                      color: Colors.black87,
                    ),
                  ),
                ),
                SizedBox(
                  height: 110,
                  child: ListView.builder(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    itemCount: _currentWeek.length,
                    itemBuilder: (context, index) {
                      final date = _currentWeek[index];
                      final dayName = _getDayName(date.weekday);
                      final isSelected = dayName == _selectedDay;
                      final isToday = dayName == _getDayName(DateTime.now().weekday);

                      return GestureDetector(
                        onTap: () {
                          setState(() {
                            _selectedDay = dayName;
                            _selectedPlanId = null;
                          });
                          exerciseProvider.loadExercisesByDay(_selectedDay);
                        },
                        child: AnimatedContainer(
                          duration: const Duration(milliseconds: 200),
                          width: 70,
                          margin: const EdgeInsets.symmetric(horizontal: 6, vertical: 10),
                          decoration: BoxDecoration(
                            color: isSelected ? const Color(0xFF1E1E1E) : Colors.grey[100],
                            borderRadius: BorderRadius.circular(18),
                            border: isSelected 
                              ? Border.all(color: Colors.deepPurple, width: 2)
                              : Border.all(color: Colors.transparent),
                            boxShadow: [
                              BoxShadow(
                                color: isSelected 
                                  ? Colors.deepPurple.withOpacity(0.2) 
                                  : Colors.black.withOpacity(0.05),
                                blurRadius: 10,
                                offset: const Offset(0, 4),
                              )
                            ],
                          ),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Text(
                                _getDayAbbr(date.weekday),
                                style: TextStyle(
                                  color: isSelected ? Colors.grey[400] : Colors.grey[600],
                                  fontSize: 12,
                                  fontWeight: FontWeight.bold,
                                  letterSpacing: 1,
                                ),
                              ),
                              const SizedBox(height: 10),
                              Container(
                                padding: const EdgeInsets.all(10),
                                decoration: BoxDecoration(
                                  color: isSelected ? Colors.limeAccent.shade700 : (isToday ? Colors.deepPurple.withOpacity(0.1) : Colors.transparent),
                                  shape: BoxShape.circle,
                                ),
                                child: Text(
                                  date.day.toString(),
                                  style: TextStyle(
                                    color: isSelected ? Colors.black : (isToday ? Colors.deepPurple : Colors.black87),
                                    fontWeight: FontWeight.bold,
                                    fontSize: 16,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
                ),
              ],
            ),
          ),

          // Today's Section
          SliverToBoxAdapter(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 15, 20, 10),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        _selectedDay == _getDayName(DateTime.now().weekday) ? "Today's Exercises" : "$_selectedDay's Exercises",
                        style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                              fontWeight: FontWeight.bold,
                              color: Colors.black87,
                            ),
                      ),
                      TextButton.icon(
                        onPressed: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(builder: (context) => const Saveexercise(isExtra: true)),
                          );
                        },
                        icon: const Icon(Icons.add_circle_outline, size: 20),
                        label: const Text("Extra"),
                        style: TextButton.styleFrom(foregroundColor: Colors.deepPurple),
                      ),
                    ],
                  ),
                ),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 5),
                  child: Row(
                    children: [
                      Padding(
                        padding: const EdgeInsets.only(right: 8),
                        child: ChoiceChip(
                          label: const Text("All"),
                          selected: _selectedPlanId == null,
                          onSelected: (selected) {
                            setState(() => _selectedPlanId = null);
                          },
                        ),
                      ),
                      ...(planProvider.plansByDay[dayName] ?? []).map((plan) {
                        return Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: ChoiceChip(
                            label: Text(plan.planName),
                            selected: _selectedPlanId == plan.planId,
                            onSelected: (selected) {
                              setState(() => _selectedPlanId = selected ? plan.planId : null);
                            },
                          ),
                        );
                      }).toList(),
                      if (exerciseProvider.extraExercises.isNotEmpty && _selectedDay == _getDayName(DateTime.now().weekday))
                        Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: ChoiceChip(
                            label: const Text("Extra"),
                            selected: _selectedPlanId == -1,
                            onSelected: (selected) {
                              setState(() => _selectedPlanId = selected ? -1 : null);
                            },
                          ),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          
          (exerciseProvider.todayExercises.isEmpty && 
           (exerciseProvider.extraExercises.isEmpty || _selectedDay != _getDayName(DateTime.now().weekday)))
              ? SliverToBoxAdapter(
                  child: Container(
                    height: 120,
                    margin: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                    decoration: BoxDecoration(
                      color: Colors.grey[100],
                      borderRadius: BorderRadius.circular(15),
                      border: Border.all(color: Colors.grey[300]!),
                    ),
                    child: Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(Icons.event_note, color: Colors.grey, size: 30),
                          const SizedBox(height: 8),
                          Text(
                            "No plans for $dayName",
                            style: TextStyle(color: Colors.grey[600]),
                          ),
                        ],
                      ),
                    ),
                  ),
                )
              : SliverList(
                  delegate: SliverChildBuilderDelegate(
                    (context, index) {
                      final todayPlans = planProvider.plansByDay[dayName] ?? [];
                      final bool isRealToday = _selectedDay == _getDayName(DateTime.now().weekday);
                      
                      // Combine exercises for the list
                      List<dynamic> combinedList = [];
                      
                      if (_selectedPlanId == null) {
                        combinedList.addAll(exerciseProvider.todayExercises);
                        if (isRealToday) combinedList.addAll(exerciseProvider.extraExercises);
                      } else if (_selectedPlanId == -1) {
                        if (isRealToday) combinedList.addAll(exerciseProvider.extraExercises);
                      } else {
                        combinedList.addAll(exerciseProvider.todayExercises.where((e) => e.planId == _selectedPlanId));
                      }

                      if (index >= combinedList.length) return null;

                      final exercise = combinedList[index];
                      
                      bool showHeader = false;
                      String planName = "";
                      
                      if (_selectedPlanId == null) {
                        if (exercise.isExtra) {
                          planName = "Extra Workouts";
                          // Check if this is the first extra exercise
                          if (exerciseProvider.extraExercises.isNotEmpty && 
                              exerciseProvider.extraExercises[0].id == exercise.id) {
                            showHeader = true;
                          }
                        } else {
                          final plan = todayPlans.firstWhere((p) => p.planId == exercise.planId, orElse: () => todayPlans.first);
                          planName = plan.planName;
                          int firstIndex = combinedList.indexWhere((e) => !e.isExtra && e.planId == exercise.planId);
                          if (firstIndex == index) {
                            showHeader = true;
                          }
                        }
                      }

                      return Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          if (showHeader)
                            Padding(
                              padding: const EdgeInsets.fromLTRB(20, 15, 20, 5),
                              child: Row(
                                children: [
                                  Container(
                                    width: 4,
                                    height: 16,
                                    decoration: BoxDecoration(
                                      color: exercise.isExtra ? Colors.orange : Colors.deepPurple,
                                      borderRadius: BorderRadius.circular(2),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  Text(
                                    planName,
                                    style: TextStyle(
                                      fontWeight: FontWeight.bold,
                                      fontSize: 14,
                                      color: exercise.isExtra ? Colors.orange : Colors.deepPurple,
                                      letterSpacing: 0.5,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                            child: Card(
                              elevation: 0,
                              color: exercise.isCheck ? Colors.green[50] : Colors.white,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(15),
                                side: BorderSide(
                                  color: exercise.isCheck ? Colors.green.withOpacity(0.2) : Colors.grey[200]!,
                                  width: 1,
                                ),
                              ),
                              child: ListTile(
                                contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
                                title: Row(
                                  children: [
                                    Expanded(
                                      child: Text(
                                        exercise.name,
                                        style: TextStyle(
                                          fontWeight: FontWeight.bold,
                                          decoration: exercise.isCheck ? TextDecoration.lineThrough : null,
                                          color: exercise.isCheck ? Colors.grey : Colors.black87,
                                        ),
                                      ),
                                    ),
                                    if (exercise.isExtra && !exercise.isCheck)
                                      IconButton(
                                        icon: const Icon(Icons.delete_outline, color: Colors.red, size: 20),
                                        onPressed: () {
                                          exerciseProvider.deleteExercise(exercise.id!, -1);
                                        },
                                      ),
                                  ],
                                ),
                                subtitle: Text(
                                  "${exercise.sets} sets • ${exercise.reps} reps • ${exercise.weight}kg",
                                  style: TextStyle(color: Colors.grey[600]),
                                ),
                                trailing: Checkbox(
                                  value: exercise.isCheck,
                                  activeColor: Colors.green,
                                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                                  onChanged: (value) {
                                    exercise.isCheck = value ?? false;
                                    exerciseProvider.updateExercise(exercise);
                                  },
                                ),
                                onTap: () async {
                                  if (exercise.isCheck) {
                                    // If completed, show reading mode with stored data
                                    Navigator.push(
                                      context,
                                      MaterialPageRoute(
                                        builder: (context) => ExerciseDetailsScreen(
                                          exercise: exercise,
                                          plan: exercise.isExtra 
                                              ? Plan(planId: -1, planName: "Extra Workouts", planDay: _selectedDay)
                                              : todayPlans.firstWhere((p) => p.planId == exercise.planId, orElse: () => todayPlans.first),
                                        ),
                                      ),
                                    );
                                  } else {
                                    // If not completed, go straight to active workout
                                    if (exercise.isExtra) {
                                      await exerciseProvider.loadExercisesByPlan(-1);
                                      if (context.mounted) {
                                        Navigator.push(
                                          context,
                                          MaterialPageRoute(
                                            builder: (context) => WorkoutPlanPage(
                                              plan: Plan(planId: -1, planName: "Extra Workouts", planDay: _selectedDay),
                                              initialExerciseIndex: exerciseProvider.exercises.indexWhere((e) => e.id == exercise.id),
                                            ),
                                          ),
                                        );
                                      }
                                    } else {
                                      final plan = todayPlans.firstWhere((p) => p.planId == exercise.planId, orElse: () => todayPlans.first);
                                      await exerciseProvider.loadExercisesByPlan(plan.planId!);
                                      int indexInPlan = exerciseProvider.exercises.indexWhere((e) => e.id == exercise.id);
                                      if (context.mounted) {
                                        Navigator.push(
                                          context,
                                          MaterialPageRoute(
                                            builder: (context) => WorkoutPlanPage(
                                              plan: plan,
                                              initialExerciseIndex: indexInPlan != -1 ? indexInPlan : 0,
                                            ),
                                          ),
                                        );
                                      }
                                    }
                                  }
                                },
                              ),
                            ),
                          ),
                        ],
                      );
                    },
                    childCount: _calculateChildCount(exerciseProvider),
                  ),
                ),

          // Training Schedule (Days)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 25, 10, 10),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    "Manage Schedule",
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                          color: Colors.black87,
                        ),
                  ),
                  IconButton(
                    onPressed: () {
                      Navigator.push(context, MaterialPageRoute(builder: (context) => const SavePlan()));
                    },
                    icon: const Icon(Icons.add_circle, color: Colors.deepPurple, size: 30),
                  ),
                ],
              ),
            ),
          ),
          
          SliverList(
            delegate: SliverChildBuilderDelegate(
              (context, index) {
                final day = _allDays[index];
                final plansForDay = groupedPlans[day] ?? [];
                final isToday = day == dayName;

                return Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                  child: InkWell(
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => DayPlansScreen(day: day),
                        ),
                      );
                    },
                    borderRadius: BorderRadius.circular(15),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(15),
                        border: Border.all(
                          color: isToday ? Colors.deepPurple.withOpacity(0.3) : Colors.grey[200]!,
                          width: isToday ? 2 : 1,
                        ),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withOpacity(0.03),
                            blurRadius: 10,
                            offset: const Offset(0, 4),
                          )
                        ]
                      ),
                      child: Row(
                        children: [
                          CircleAvatar(
                            backgroundColor: isToday ? Colors.deepPurple : Colors.grey[100],
                            radius: 18,
                            child: Text(
                              day[0],
                              style: TextStyle(
                                color: isToday ? Colors.white : Colors.grey[600],
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                          const SizedBox(width: 15),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  day,
                                  style: TextStyle(
                                    fontWeight: isToday ? FontWeight.bold : FontWeight.w500,
                                    fontSize: 16,
                                    color: isToday ? Colors.deepPurple : Colors.black87,
                                  ),
                                ),
                                Text(
                                  "${plansForDay.length} Plans",
                                  style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                                ),
                              ],
                            ),
                          ),
                          const Icon(Icons.arrow_forward_ios, size: 14, color: Colors.grey),
                        ],
                      ),
                    ),
                  ),
                );
              },
              childCount: _allDays.length,
            ),
          ),
          const SliverPadding(padding: EdgeInsets.only(bottom: 80)),
        ],
      ),
    );
  }

  int _calculateChildCount(Exerciseprovider provider) {
    final bool isRealToday = _selectedDay == _getDayName(DateTime.now().weekday);
    if (_selectedPlanId == null) {
      return provider.todayExercises.length + (isRealToday ? provider.extraExercises.length : 0);
    } else if (_selectedPlanId == -1) {
      return isRealToday ? provider.extraExercises.length : 0;
    } else {
      return provider.todayExercises.where((e) => e.planId == _selectedPlanId).length;
    }
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

  String _getDayAbbr(int weekday) {
    switch (weekday) {
      case 1: return "MON";
      case 2: return "TUE";
      case 3: return "WED";
      case 4: return "THU";
      case 5: return "FRI";
      case 6: return "SAT";
      case 7: return "SUN";
      default: return "";
    }
  }
}
