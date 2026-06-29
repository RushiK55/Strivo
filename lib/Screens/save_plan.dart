import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:strivo/Screens/PlanDetailsScreen.dart';
import 'package:strivo/models/Plan.dart';
import 'package:strivo/providers/PlanProvider.dart';
import 'package:strivo/utils/app_colors.dart';

class SavePlan extends StatefulWidget {
  final String? initialDay;
  const SavePlan({super.key, this.initialDay});

  @override
  State<SavePlan> createState() => _SavePlanState();
}

class _SavePlanState extends State<SavePlan> {
  final _formKey = GlobalKey<FormState>();
  final TextEditingController _planNameController = TextEditingController();
  late String _selectedDay;

  final List<String> _days = [
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
    _selectedDay = widget.initialDay ?? 'Monday';
  }

  @override
  void dispose() {
    _planNameController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final planProvider = Provider.of<Planprovider>(context);
    final groupedPlans = planProvider.plansByDay;
    final plansForSelectedDay = groupedPlans[_selectedDay] ?? [];

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text("Create New Plan",
            style: TextStyle(fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
        elevation: 0,
        backgroundColor: AppColors.background,
        foregroundColor: AppColors.textPrimary,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 20, color: AppColors.textPrimary),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: SingleChildScrollView(
        physics: const BouncingScrollPhysics(),
        padding: const EdgeInsets.all(24.0),
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    "PLAN DETAILS",
                    style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.bold,
                        color: AppColors.textPrimary,
                        letterSpacing: 1.2),
                  ),
                  const SizedBox(height: 4),
                  Container(
                    width: 40,
                    height: 3,
                    decoration: BoxDecoration(
                      color: AppColors.accent,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 30),
              TextFormField(
                controller: _planNameController,
                style: const TextStyle(color: AppColors.textPrimary),
                decoration: InputDecoration(
                  labelText: 'Plan Name',
                  labelStyle: const TextStyle(color: AppColors.textSecondary),
                  hintText: 'e.g. Chest & Triceps',
                  hintStyle: const TextStyle(color: Color(0xFF5E5E5E)),
                  prefixIcon: const Icon(Icons.fitness_center_rounded, color: AppColors.accent),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(20),
                    borderSide: const BorderSide(color: AppColors.surface),
                  ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(20),
                    borderSide: const BorderSide(color: AppColors.accent),
                  ),
                  filled: true,
                  fillColor: AppColors.surface,
                ),
                validator: (value) {
                  if (value == null || value.isEmpty) {
                    return 'Please enter a plan name';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 30),
              const Text(
                "SELECT DAY",
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                  color: AppColors.textPrimary,
                  letterSpacing: 1.2,
                ),
              ),
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(20),
                  color: AppColors.surface,
                ),
                child: DropdownButtonHideUnderline(
                  child: DropdownButton<String>(
                    isExpanded: true,
                    value: _selectedDay,
                    dropdownColor: AppColors.surface,
                    style: const TextStyle(color: AppColors.textPrimary, fontSize: 16),
                    icon: const Icon(Icons.keyboard_arrow_down_rounded, color: AppColors.accent),
                    items: _days.map((String day) {
                      final hasPlan = (groupedPlans[day] ?? []).isNotEmpty;
                      return DropdownMenuItem<String>(
                        value: day,
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(day, style: const TextStyle(fontWeight: FontWeight.w500)),
                            if (hasPlan)
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                                decoration: BoxDecoration(
                                  color: AppColors.accent.withOpacity(0.1),
                                  borderRadius: BorderRadius.circular(10),
                                  border: Border.all(color: AppColors.accent.withOpacity(0.3)),
                                ),
                                child: Text(
                                  "${groupedPlans[day]!.length} Plan(s)",
                                  style: const TextStyle(fontSize: 10, color: AppColors.accent, fontWeight: FontWeight.bold),
                                ),
                              ),
                          ],
                        ),
                      );
                    }).toList(),
                    onChanged: (String? newValue) {
                      setState(() {
                        _selectedDay = newValue!;
                      });
                    },
                  ),
                ),
              ),
              if (plansForSelectedDay.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 16, left: 4),
                  child: Row(
                    children: [
                      const Icon(Icons.info_outline_rounded, size: 18, color: AppColors.accent),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          "You already have ${plansForSelectedDay.length} plan(s) for $_selectedDay.",
                          style: const TextStyle(color: AppColors.textSecondary, fontSize: 13),
                        ),
                      ),
                    ],
                  ),
                ),
              const SizedBox(height: 60),
              SizedBox(
                width: double.infinity,
                height: 55,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.accent,
                    foregroundColor: Colors.black,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(15),
                    ),
                    elevation: 0,
                  ),
                  onPressed: () async {
                    if (_formKey.currentState!.validate()) {
                      int id = await planProvider.addPlan(
                        Plan(
                          planName: _planNameController.text,
                          planDay: _selectedDay,
                        ),
                      );
                      if (mounted) {
                        savePlan(id, _planNameController.text, _selectedDay);
                      }
                    }
                  },
                  child: const Text(
                    "SAVE PLAN",
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void savePlan(int id, String planName, String day) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.surface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(25)),
        title: const Text("Plan Saved", style: TextStyle(color: AppColors.textPrimary, fontWeight: FontWeight.bold)),
        content: Text("Plan '$planName' has been added to $day.", style: const TextStyle(color: AppColors.textSecondary)),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.pop(context); // Close dialog
              Navigator.pushReplacement(
                context,
                MaterialPageRoute(
                  builder: (context) => PlanDetailsScreen(
                    plan: Plan(planId: id, planName: planName, planDay: day),
                  ),
                ),
              );
            },
            child: const Text("OK", style: TextStyle(color: AppColors.accent, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }
}
