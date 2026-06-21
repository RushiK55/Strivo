import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:strivo/Screens/ExerciseList.dart';
import 'package:strivo/Screens/save_plan.dart';
import 'package:strivo/models/Plan.dart';
import 'package:strivo/providers/ExerciseProvider.dart';
import 'package:strivo/providers/PlanProvider.dart';

class DayPlansScreen extends StatelessWidget {
  final String day;

  const DayPlansScreen({super.key, required this.day});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.grey[50],
      appBar: AppBar(
        title: Text("$day Plans"),
        elevation: 0,
        backgroundColor: Colors.white,
        foregroundColor: Colors.black,
      ),
      body: Consumer<Planprovider>(
        builder: (context, planProvider, child) {
          final plansForDay = planProvider.plansByDay[day] ?? [];

          if (plansForDay.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.calendar_today, size: 80, color: Colors.grey[300]),
                  const SizedBox(height: 16),
                  Text(
                    "No plans for $day",
                    style: const TextStyle(color: Colors.grey, fontSize: 16),
                  ),
                  const SizedBox(height: 24),
                  ElevatedButton.icon(
                    onPressed: () => _navigateToAddPlan(context),
                    icon: const Icon(Icons.add),
                    label: const Text("Add First Plan"),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.deepPurple,
                      foregroundColor: Colors.white,
                    ),
                  )
                ],
              ),
            );
          }

          return ListView.builder(
            padding: const EdgeInsets.all(16),
            itemCount: plansForDay.length,
            itemBuilder: (context, index) {
              final plan = plansForDay[index];
              return Card(
                margin: const EdgeInsets.only(bottom: 12),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(15)),
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
                  title: Text(
                    plan.planName,
                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
                  ),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        icon: const Icon(Icons.delete_outline, color: Colors.red),
                        onPressed: () => _showDeletePlanConfirm(context, planProvider, plan),
                      ),
                      const Icon(Icons.arrow_forward_ios, size: 16),
                    ],
                  ),
                  onTap: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) => Exerciselist(plan: plan),
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
        backgroundColor: Colors.deepPurple,
        child: const Icon(Icons.add),
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

  void _showDeletePlanConfirm(BuildContext context, Planprovider planProvider, Plan plan) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text("Delete Plan?"),
        content: Text("This will permanently delete the '${plan.planName}' plan and all its exercises."),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text("Cancel")),
          TextButton(
            onPressed: () async {
              await planProvider.deletePlan(plan.planId!);
              if (context.mounted) {
                Provider.of<Exerciseprovider>(context, listen: false).loadTodayExercises();
                Navigator.pop(context);
              }
            },
            child: const Text("Delete", style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
  }
}
