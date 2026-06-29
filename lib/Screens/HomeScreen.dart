import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:strivo/Screens/WorkoutPlanPage.dart';
import 'package:strivo/Screens/HistoryScreen.dart';
import 'package:strivo/Screens/PlanDetailsScreen.dart';
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
import 'package:strivo/utils/app_colors.dart';

class Homescreen extends StatefulWidget {
  const Homescreen({super.key});

  @override
  State<Homescreen> createState() => _HomescreenState();
}

class _HomescreenState extends State<Homescreen> {
  late String _selectedDay;
  late List<DateTime> _currentWeek;
  final ScrollController _scrollController = ScrollController();
  
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
      _scrollToToday();
    });
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _scrollToToday() {
    if (!_scrollController.hasClients) return;

    final now = DateTime.now();
    int todayIndex = now.weekday - 1;
    double itemWidth = 87.0; // 75 (width) + 12 (horizontal margins)
    double padding = 16.0;

    double screenWidth = MediaQuery.of(context).size.width;
    double scrollOffset = (todayIndex * itemWidth) + padding;

    // Center the today's item
    double targetOffset = scrollOffset - (screenWidth / 2) + (itemWidth / 2);

    _scrollController.animateTo(
      targetOffset.clamp(0.0, _scrollController.position.maxScrollExtent),
      duration: const Duration(milliseconds: 500),
      curve: Curves.easeInOut,
    );
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
      builder: (context) => Dialog(
        backgroundColor: AppColors.surface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(30)),
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                "Weight Update",
                style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
              ),
              const SizedBox(height: 8),
              const Text(
                "Keep your progress on track!",
                style: TextStyle(fontSize: 14, color: AppColors.textSecondary),
              ),
              const SizedBox(height: 30),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  WheelPicker(
                    label: "KG",
                    minValue: 30,
                    maxValue: 250,
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
              const SizedBox(height: 30),
              ElevatedButton(
                onPressed: () async {
                  await UserManager.updateWeightOnly(wInt + (wDec / 10.0));
                  if (context.mounted) Navigator.pop(context);
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.accent,
                  foregroundColor: Colors.black,
                  minimumSize: const Size(double.infinity, 50),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(15)),
                ),
                child: const Text("UPDATE", style: TextStyle(fontWeight: FontWeight.bold)),
              ),
            ],
          ),
        ),
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
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: CustomScrollView(
          physics: const BouncingScrollPhysics(),
          slivers: [
            // Top Navigation & Branding
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                child: Column(
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Container(
                          padding: const EdgeInsets.all(8),
                          decoration: BoxDecoration(
                            color: AppColors.accent.withOpacity(0.2),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: const Icon(Icons.fitness_center, color: AppColors.accent, size: 24),
                        ),
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.all(8),
                              decoration: BoxDecoration(
                                color: AppColors.surface,
                                shape: BoxShape.circle,
                              ),
                              child: const Icon(Icons.access_time, color: AppColors.textPrimary, size: 20),
                            ),
                            const SizedBox(width: 12),
                            GestureDetector(
                              onTap: () {
                                Navigator.push(
                                  context,
                                  MaterialPageRoute(builder: (context) => const ProfileScreen()),
                                );
                              },
                              child: Container(
                                padding: const EdgeInsets.all(8),
                                decoration: BoxDecoration(
                                  color: AppColors.surface,
                                  shape: BoxShape.circle,
                                ),
                                child: const Icon(Icons.person_outline, color: AppColors.textPrimary, size: 20),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                    const SizedBox(height: 30),
                    const Text(
                      "Strivo",
                      style: TextStyle(
                        color: AppColors.textPrimary,
                        fontSize: 24,
                        fontWeight: FontWeight.w900,
                        letterSpacing: 2.0,
                      ),
                    ),
                    Text(
                      _getDayName(DateTime.now().weekday),
                      style: const TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    const SizedBox(height: 40),
                    Align(
                      alignment: Alignment.center,
                      child: Text(
                        "Hello, ${authProvider.userName?.split(' ')[0] ?? 'User'}",
                        style: const TextStyle(
                          color: AppColors.textPrimary,
                          fontSize: 42,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                    ),
                    const SizedBox(height: 10),
                  ],
                ),
              ),
            ),

            // Weekly Schedule Selector Header
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 40, 20, 10),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          "Weekly Schedule",
                          style: TextStyle(
                            fontSize: 24,
                            fontWeight: FontWeight.bold,
                            color: AppColors.textPrimary,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Container(
                          width: 60,
                          height: 3,
                          decoration: BoxDecoration(
                            color: AppColors.accent,
                            borderRadius: BorderRadius.circular(2),
                          ),
                        ),
                      ],
                    ),
                    GestureDetector(
                      onTap: () {
                        Navigator.push(
                            context,
                            MaterialPageRoute(
                                builder: (context) => const SavePlan()));
                      },
                      child: Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: AppColors.accent,
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(Icons.add, color: Colors.black, size: 24),
                      ),
                    ),
                  ],
                ),
              ),
            ),

            // Weekly Schedule List
            SliverToBoxAdapter(
              child: SizedBox(
                height: 140,
                child: ListView.builder(
                  controller: _scrollController,
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  itemCount: _currentWeek.length,
                  itemBuilder: (context, index) {
                    final date = _currentWeek[index];
                    final dayName = _getDayName(date.weekday);
                    final isSelected = dayName == _selectedDay;

                    return GestureDetector(
                      onTap: () {
                        setState(() {
                          _selectedDay = dayName;
                        });
                        exerciseProvider.loadExercisesByDay(_selectedDay);
                      },
                      child: AnimatedContainer(
                        duration: const Duration(milliseconds: 200),
                        width: 75,
                        margin: const EdgeInsets.symmetric(horizontal: 6, vertical: 10),
                        decoration: BoxDecoration(
                          color: isSelected ? Colors.transparent : AppColors.surface,
                          borderRadius: BorderRadius.circular(25),
                          border: isSelected
                              ? Border.all(color: AppColors.accent, width: 2)
                              : Border.all(color: Colors.transparent),
                        ),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(
                              _getDayAbbr(date.weekday),
                              style: TextStyle(
                                color: AppColors.textSecondary,
                                fontSize: 12,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 15),
                            Container(
                              width: 40,
                              height: 60,
                              decoration: BoxDecoration(
                                color: isSelected ? AppColors.accent : Colors.transparent,
                                borderRadius: BorderRadius.circular(20),
                              ),
                              alignment: Alignment.center,
                              child: Text(
                                date.day.toString(),
                                style: TextStyle(
                                  color: isSelected ? Colors.black : AppColors.textPrimary,
                                  fontWeight: FontWeight.bold,
                                  fontSize: 18,
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
            ),

            // Plans Header
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 20, 20, 10),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _selectedDay == _getDayName(DateTime.now().weekday)
                          ? "Today's Plans"
                          : "$_selectedDay's Plans",
                      style: const TextStyle(
                        fontSize: 24,
                        fontWeight: FontWeight.bold,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Container(
                      width: 60,
                      height: 3,
                      decoration: BoxDecoration(
                        color: AppColors.accent,
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ],
                ),
              ),
            ),

            (groupedPlans[dayName] == null || groupedPlans[dayName]!.isEmpty)
                ? SliverToBoxAdapter(
                    child: Container(
                      height: 120,
                      margin: const EdgeInsets.symmetric(
                          horizontal: 20, vertical: 10),
                      decoration: BoxDecoration(
                        color: AppColors.surface,
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Icon(Icons.event_note,
                                color: AppColors.textSecondary, size: 30),
                            const SizedBox(height: 8),
                            Text(
                              "No plans for $dayName",
                              style: const TextStyle(color: AppColors.textSecondary),
                            ),
                          ],
                        ),
                      ),
                    ),
                  )
                : SliverList(
                    delegate: SliverChildBuilderDelegate(
                      (context, index) {
                        final plan = groupedPlans[dayName]![index];
                        return Padding(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 20, vertical: 8),
                          child: Container(
                            decoration: BoxDecoration(
                              color: AppColors.surface,
                              borderRadius: BorderRadius.circular(20),
                            ),
                            child: ListTile(
                              contentPadding: const EdgeInsets.symmetric(
                                  horizontal: 20, vertical: 15),
                              leading: Container(
                                padding: const EdgeInsets.all(12),
                                decoration: BoxDecoration(
                                  color: AppColors.accent.withOpacity(0.1),
                                  shape: BoxShape.circle,
                                  border: Border.all(color: AppColors.accent.withOpacity(0.2), width: 1),
                                ),
                                child: const Icon(Icons.fitness_center_rounded,
                                    color: AppColors.accent, size: 24),
                              ),
                              title: Text(
                                plan.planName,
                                style: const TextStyle(
                                  fontWeight: FontWeight.bold,
                                  color: AppColors.textPrimary,
                                  fontSize: 20,
                                ),
                              ),
                              subtitle: const Padding(
                                padding: EdgeInsets.only(top: 4),
                                child: Text(
                                  "Tap to view exercises",
                                  style: TextStyle(color: AppColors.textSecondary, fontSize: 13),
                                ),
                              ),
                              trailing: Container(
                                padding: const EdgeInsets.all(4),
                                decoration: BoxDecoration(
                                  color: const Color(0xFF2C2C2E),
                                  shape: BoxShape.circle,
                                ),
                                child: const Icon(Icons.arrow_forward_ios_rounded,
                                    size: 12, color: AppColors.textPrimary),
                              ),
                              onTap: () {
                                Navigator.push(
                                  context,
                                  MaterialPageRoute(
                                    builder: (context) =>
                                        PlanDetailsScreen(plan: plan),
                                  ),
                                );
                              },
                            ),
                          ),
                        );
                      },
                      childCount: groupedPlans[dayName]?.length ?? 0,
                    ),
                  ),

            const SliverPadding(padding: EdgeInsets.only(bottom: 100)),
          ],
        ),
      ),
    );
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
