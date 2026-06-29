import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:strivo/Screens/PlanDetailsScreen.dart';
import 'package:strivo/Screens/save_plan.dart';
import 'package:strivo/models/Plan.dart';
import 'package:strivo/providers/ExerciseProvider.dart';
import 'package:strivo/providers/PlanProvider.dart';
import 'package:strivo/utils/app_colors.dart';

class DayPlansScreen extends StatelessWidget {
  final String day;

  const DayPlansScreen({super.key, required this.day});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: Text("$day Plans",
            style: const TextStyle(fontWeight: FontWeight.bold, color: AppColors.textPrimary, fontSize: 22)),
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
      body: Consumer<Planprovider>(
        builder: (context, planProvider, child) {
          final plansForDay = planProvider.plansByDay[day] ?? [];

          if (plansForDay.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    padding: const EdgeInsets.all(30),
                    decoration: BoxDecoration(
                      color: AppColors.surface,
                      shape: BoxShape.circle,
                    ),
                    child: Icon(Icons.calendar_today_rounded, size: 60, color: AppColors.accent.withOpacity(0.5)),
                  ),
                  const SizedBox(height: 24),
                  Text(
                    "No plans scheduled for $day",
                    style: const TextStyle(color: AppColors.textSecondary, fontSize: 16, fontWeight: FontWeight.w500),
                  ),
                  const SizedBox(height: 40),
                  SizedBox(
                    height: 55,
                    width: 200,
                    child: ElevatedButton.icon(
                      onPressed: () => _navigateToAddPlan(context),
                      icon: const Icon(Icons.add_rounded, color: Colors.black),
                      label: const Text("CREATE PLAN",
                          style: TextStyle(fontWeight: FontWeight.bold, letterSpacing: 1.2)),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.accent,
                        foregroundColor: Colors.black,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(15)),
                        elevation: 0,
                      ),
                    ),
                  )
                ],
              ),
            );
          }

          return ListView.builder(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
            physics: const BouncingScrollPhysics(),
            itemCount: plansForDay.length,
            itemBuilder: (context, index) {
              final plan = plansForDay[index];
              return Container(
                margin: const EdgeInsets.only(bottom: 16),
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(25),
                  border: Border.all(color: const Color(0xFF2C2C2E)),
                ),
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 15),
                  leading: Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: AppColors.accent.withOpacity(0.1),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.fitness_center_rounded, color: AppColors.accent, size: 24),
                  ),
                  title: Text(
                    plan.planName,
                    style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 20,
                        color: AppColors.textPrimary),
                  ),
                  subtitle: const Padding(
                    padding: EdgeInsets.only(top: 4),
                    child: Text("Manage exercises", style: TextStyle(color: AppColors.textSecondary)),
                  ),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        icon: const Icon(Icons.delete_outline_rounded, color: Colors.redAccent),
                        onPressed: () =>
                            _showDeletePlanConfirm(context, planProvider, plan),
                      ),
                      const Icon(Icons.arrow_forward_ios_rounded,
                          size: 16, color: AppColors.textSecondary),
                    ],
                  ),
                  onTap: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) => PlanDetailsScreen(plan: plan),
                      ),
                    );
                  },
                ),
              );
            },
          );
        },
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _navigateToAddPlan(context),
        backgroundColor: AppColors.accent,
        foregroundColor: Colors.black,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        elevation: 4,
        child: const Icon(Icons.add_rounded, size: 32),
      ),
    );
  }

  void _navigateToAddPlan(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => SavePlan(initialDay: day),
      ),
    );
  }

  void _showDeletePlanConfirm(
      BuildContext context, Planprovider planProvider, Plan plan) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.surface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(25)),
        title: const Text("Delete Plan?",
            style: TextStyle(color: AppColors.textPrimary, fontWeight: FontWeight.bold)),
        content: Text(
            "This will permanently delete the '${plan.planName}' plan and all its exercises.",
            style: const TextStyle(color: AppColors.textSecondary)),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text("Cancel", style: TextStyle(color: AppColors.textSecondary))),
          TextButton(
            onPressed: () async {
              await planProvider.deletePlan(plan.planId!);
              if (context.mounted) {
                Provider.of<Exerciseprovider>(context, listen: false)
                    .loadTodayExercises();
                Navigator.pop(context);
              }
            },
            child: const Text("Delete", style: TextStyle(color: Colors.redAccent)),
          ),
        ],
      ),
    );
  }
}
