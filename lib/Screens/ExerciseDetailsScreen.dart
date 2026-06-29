import 'package:flutter/material.dart';
import 'package:strivo/models/Exercise.dart';
import 'package:strivo/models/Plan.dart';
import 'package:strivo/Screens/WorkoutPlanPage.dart';
import 'package:strivo/utils/app_colors.dart';

class ExerciseDetailsScreen extends StatelessWidget {
  final Exercise exercise;
  final Plan plan;

  const ExerciseDetailsScreen({
    super.key,
    required this.exercise,
    required this.plan,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: CustomScrollView(
        physics: const BouncingScrollPhysics(),
        slivers: [
          SliverAppBar(
            expandedHeight: 250,
            pinned: true,
            backgroundColor: AppColors.background,
            elevation: 0,
            leading: IconButton(
              icon: Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  shape: BoxShape.circle,
                ),
                child: const Icon(Icons.arrow_back_ios_new, color: AppColors.textPrimary, size: 16),
              ),
              onPressed: () => Navigator.pop(context),
            ),
            flexibleSpace: FlexibleSpaceBar(
              centerTitle: true,
              title: Text(
                exercise.name,
                style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontWeight: FontWeight.bold,
                  fontSize: 22,
                ),
              ),
              background: Container(
                color: AppColors.background,
                child: Center(
                  child: Container(
                    padding: const EdgeInsets.all(30),
                    decoration: BoxDecoration(
                      color: AppColors.accent.withOpacity(0.1),
                      shape: BoxShape.circle,
                      border: Border.all(color: AppColors.accent.withOpacity(0.2), width: 2),
                    ),
                    child: const Icon(
                      Icons.fitness_center_rounded,
                      size: 80,
                      color: AppColors.accent,
                    ),
                  ),
                ),
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 20),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                _buildSectionTitle("Quick Summary"),
                const SizedBox(height: 16),
                Row(
                  children: [
                    _buildStatCard(
                      "Sets",
                      exercise.sets,
                      Icons.layers_outlined,
                      AppColors.accent,
                    ),
                    const SizedBox(width: 12),
                    _buildStatCard(
                      "Reps",
                      exercise.reps,
                      Icons.repeat_rounded,
                      AppColors.accent,
                    ),
                    const SizedBox(width: 12),
                    _buildStatCard(
                      "Weight",
                      "${exercise.weight} kg",
                      Icons.fitness_center_rounded,
                      AppColors.accent,
                    ),
                  ],
                ),
                const SizedBox(height: 40),
                if (exercise.notes.isNotEmpty) ...[
                  _buildSectionTitle("Exercise Notes"),
                  const SizedBox(height: 16),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      color: AppColors.surface,
                      borderRadius: BorderRadius.circular(25),
                      border: Border.all(color: const Color(0xFF2C2C2E)),
                    ),
                    child: Text(
                      exercise.notes,
                      style: const TextStyle(
                        color: AppColors.textPrimary,
                        height: 1.6,
                        fontSize: 16,
                      ),
                    ),
                  ),
                  const SizedBox(height: 40),
                ],
                _buildSectionTitle("Current Plan"),
                const SizedBox(height: 16),
                Container(
                  decoration: BoxDecoration(
                    color: AppColors.surface,
                    borderRadius: BorderRadius.circular(25),
                    border: Border.all(color: const Color(0xFF2C2C2E)),
                  ),
                  child: ListTile(
                    contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                    leading: Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppColors.accent.withOpacity(0.1),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.assignment_outlined, color: AppColors.accent),
                    ),
                    title: Text(
                      plan.planName,
                      style: const TextStyle(fontWeight: FontWeight.bold, color: AppColors.textPrimary, fontSize: 18),
                    ),
                    subtitle: Text(plan.planDay, style: const TextStyle(color: AppColors.textSecondary)),
                  ),
                ),
                const SizedBox(height: 40),
                if (exercise.setsList.isNotEmpty) ...[
                  _buildSectionTitle(exercise.isCheck ? "Performance History" : "Target Sets"),
                  const SizedBox(height: 16),
                ],
              ]),
            ),
          ),
          if (exercise.setsList.isNotEmpty)
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate(
                  (context, index) {
                    final set = exercise.setsList[index];
                    return Container(
                      margin: const EdgeInsets.only(bottom: 12),
                      padding: const EdgeInsets.all(20),
                      decoration: BoxDecoration(
                        color: AppColors.surface,
                        borderRadius: BorderRadius.circular(25),
                      ),
                      child: Row(
                        children: [
                          Container(
                            width: 36,
                            height: 36,
                            decoration: BoxDecoration(
                              color: set.isCompleted ? AppColors.accent : const Color(0xFF2C2C2E),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            alignment: Alignment.center,
                            child: Text(
                              "${index + 1}",
                              style: TextStyle(
                                fontSize: 14,
                                color: set.isCompleted ? Colors.black : AppColors.textPrimary,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                          const SizedBox(width: 20),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  "${set.weight} kg × ${set.reps} reps",
                                  style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                      fontSize: 18,
                                      color: AppColors.textPrimary),
                                ),
                                if (exercise.isCheck && set.setDuration.isNotEmpty && set.setDuration != "00:00:00")
                                  Padding(
                                    padding: const EdgeInsets.only(top: 4),
                                    child: Text(
                                      "Duration: ${set.setDuration}",
                                      style: const TextStyle(color: AppColors.textSecondary, fontSize: 13),
                                    ),
                                  ),
                              ],
                            ),
                          ),
                          if (set.isCompleted)
                            const Icon(Icons.check_circle_rounded, color: AppColors.accent, size: 28),
                        ],
                      ),
                    );
                  },
                  childCount: exercise.setsList.length,
                ),
              ),
            ),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(24, 40, 24, 100),
              child: SizedBox(
                width: double.infinity,
                height: 60,
                child: ElevatedButton.icon(
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) => WorkoutPlanPage(
                          plan: plan,
                        ),
                      ),
                    );
                  },
                  icon: Icon(exercise.isCheck ? Icons.refresh_rounded : Icons.play_arrow_rounded, color: Colors.black),
                  label: Text(
                    exercise.isCheck ? "RESTART WORKOUT" : "START WORKOUT",
                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: Colors.black, letterSpacing: 1.2),
                  ),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.accent,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(20),
                    ),
                    elevation: 0,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title.toUpperCase(),
          style: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
            letterSpacing: 1.2,
          ),
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
    );
  }

  Widget _buildStatCard(String label, String value, IconData icon, Color color) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 20),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(25),
        ),
        child: Column(
          children: [
            Icon(icon, color: color, size: 28),
            const SizedBox(height: 12),
            Text(
              value,
              style: const TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.w900,
                  color: AppColors.textPrimary),
            ),
            const SizedBox(height: 4),
            Text(
              label,
              style: const TextStyle(
                fontSize: 12,
                color: AppColors.textSecondary,
                fontWeight: FontWeight.bold,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
