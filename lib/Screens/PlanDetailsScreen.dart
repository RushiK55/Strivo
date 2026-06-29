import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:strivo/models/Plan.dart';
import 'package:strivo/models/Exercise.dart';
import 'package:strivo/providers/ExerciseProvider.dart';
import 'package:strivo/Screens/WorkoutPlanPage.dart';
import 'package:strivo/Screens/SaveExercise.dart';
import 'package:strivo/Screens/ExerciseDetailsScreen.dart';
import 'package:strivo/utils/app_colors.dart';

class PlanDetailsScreen extends StatefulWidget {
  final Plan plan;

  const PlanDetailsScreen({super.key, required this.plan});

  @override
  State<PlanDetailsScreen> createState() => _PlanDetailsScreenState();
}

class _PlanDetailsScreenState extends State<PlanDetailsScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      Provider.of<Exerciseprovider>(context, listen: false)
          .loadExercisesByPlan(widget.plan.planId!);
    });
  }

  @override
  Widget build(BuildContext context) {
    final exerciseProvider = Provider.of<Exerciseprovider>(context);
    final exercises = exerciseProvider.exercises;

    return Scaffold(
      backgroundColor: AppColors.background,
      body: CustomScrollView(
        physics: const BouncingScrollPhysics(),
        slivers: [
          _buildAppBar(context, exercises.length),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(24, 30, 24, 10),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        "WORKOUT SEQUENCE",
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                          color: AppColors.textPrimary,
                          letterSpacing: 1.5,
                        ),
                      ),
                      const SizedBox(height: 6),
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
                  GestureDetector(
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => Saveexercise(plan: widget.plan),
                        ),
                      );
                    },
                    child: Container(
                      padding: const EdgeInsets.all(10),
                      decoration: const BoxDecoration(
                        color: AppColors.accent,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.add_rounded, size: 22, color: Colors.black),
                    ),
                  )
                ],
              ),
            ),
          ),
          exercises.isEmpty
              ? _buildEmptyState()
              : SliverPadding(
                  padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
                  sliver: SliverList(
                    delegate: SliverChildBuilderDelegate(
                      (context, index) {
                        final exercise = exercises[index];
                        return _buildExerciseCard(exercise, index);
                      },
                      childCount: exercises.length,
                    ),
                  ),
                ),
          const SliverPadding(padding: EdgeInsets.only(bottom: 120)),
        ],
      ),
      floatingActionButton: exercises.isNotEmpty
          ? Container(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              width: double.infinity,
              height: 60,
              child: FloatingActionButton.extended(
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) => WorkoutPlanPage(plan: widget.plan),
                    ),
                  );
                },
                backgroundColor: AppColors.accent,
                foregroundColor: Colors.black,
                elevation: 0,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                icon: const Icon(Icons.play_arrow_rounded, size: 28),
                label: const Text("START WORKOUT",
                    style: TextStyle(fontWeight: FontWeight.w900, fontSize: 16, letterSpacing: 1.2)),
              ),
            )
          : null,
      floatingActionButtonLocation: FloatingActionButtonLocation.centerFloat,
    );
  }

  Widget _buildAppBar(BuildContext context, int count) {
    return SliverAppBar(
      expandedHeight: 240,
      pinned: true,
      elevation: 0,
      backgroundColor: AppColors.background,
      leading: IconButton(
        icon: Container(
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: AppColors.surface.withOpacity(0.5),
            shape: BoxShape.circle,
          ),
          child: const Icon(Icons.arrow_back_ios_new, color: AppColors.textPrimary, size: 16),
        ),
        onPressed: () => Navigator.pop(context),
      ),
      flexibleSpace: FlexibleSpaceBar(
        centerTitle: true,
        title: Text(
          widget.plan.planName,
          style: const TextStyle(
            color: AppColors.textPrimary,
            fontWeight: FontWeight.bold,
            fontSize: 22,
          ),
        ),
        background: Container(
          decoration: const BoxDecoration(
            color: AppColors.background,
          ),
          child: Stack(
            children: [
              Positioned(
                right: -40,
                top: 40,
                child: Container(
                  width: 200,
                  height: 200,
                  decoration: BoxDecoration(
                    color: AppColors.accent.withOpacity(0.03),
                    shape: BoxShape.circle,
                  ),
                ),
              ),
              Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const SizedBox(height: 20),
                    Container(
                      padding: const EdgeInsets.all(24),
                      decoration: BoxDecoration(
                        color: AppColors.accent.withOpacity(0.1),
                        shape: BoxShape.circle,
                        border: Border.all(color: AppColors.accent.withOpacity(0.2), width: 2),
                      ),
                      child: const Icon(
                        Icons.fitness_center_rounded,
                        size: 60,
                        color: AppColors.accent,
                      ),
                    ),
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                      decoration: BoxDecoration(
                        color: AppColors.surface,
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(color: AppColors.accent.withOpacity(0.3)),
                      ),
                      child: Text(
                        "${widget.plan.planDay} • $count EXERCISES",
                        style: const TextStyle(
                          color: AppColors.accent,
                          fontWeight: FontWeight.bold,
                          fontSize: 12,
                          letterSpacing: 1.5,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildEmptyState() {
    return SliverFillRemaining(
      hasScrollBody: false,
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(30),
              decoration: BoxDecoration(
                color: AppColors.surface,
                shape: BoxShape.circle,
              ),
              child: Icon(Icons.list_alt_rounded, size: 60, color: AppColors.accent.withOpacity(0.3)),
            ),
            const SizedBox(height: 24),
            const Text(
              "No exercises in this plan",
              style: TextStyle(color: AppColors.textSecondary, fontSize: 16, fontWeight: FontWeight.w500),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildExerciseCard(Exercise exercise, int index) {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(30),
        border: Border.all(color: const Color(0xFF2C2C2E)),
      ),
      child: Column(
        children: [
          ListTile(
            contentPadding: const EdgeInsets.fromLTRB(20, 10, 10, 10),
            leading: Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: exercise.isCheck
                    ? AppColors.accent.withOpacity(0.2)
                    : const Color(0xFF2C2C2E),
                shape: BoxShape.circle,
              ),
              child: Center(
                child: exercise.isCheck
                    ? const Icon(Icons.check_rounded, color: AppColors.accent, size: 24)
                    : Text(
                        "${index + 1}",
                        style: const TextStyle(
                          color: AppColors.textPrimary,
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
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
            subtitle: Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text(
                "${exercise.sets} SETS • ${exercise.reps} REPS • ${exercise.weight} KG",
                style: const TextStyle(color: AppColors.textSecondary, fontSize: 12, fontWeight: FontWeight.w600),
              ),
            ),
            trailing: PopupMenuButton<String>(
              onSelected: (value) => _handleMenuAction(value, exercise),
              color: AppColors.surface,
              elevation: 4,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
              itemBuilder: (context) => [
                const PopupMenuItem(
                    value: 'details',
                    child: Text('View Details', style: TextStyle(color: AppColors.textPrimary))),
                const PopupMenuItem(
                    value: 'edit',
                    child: Text('Edit', style: TextStyle(color: AppColors.textPrimary))),
                const PopupMenuItem(
                    value: 'delete',
                    child: Text('Delete', style: TextStyle(color: Colors.redAccent))),
              ],
              icon: const Icon(Icons.more_vert_rounded, color: AppColors.textSecondary),
            ),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => ExerciseDetailsScreen(
                    exercise: exercise,
                    plan: widget.plan,
                  ),
                ),
              );
            },
          ),
          if (exercise.setsList.isNotEmpty) ...[
            Padding(
              padding: const EdgeInsets.fromLTRB(80, 0, 20, 20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: exercise.setsList.take(5).map((set) {
                      return Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                        decoration: BoxDecoration(
                          color: const Color(0xFF2C2C2E),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          "${set.weight}kg × ${set.reps}",
                          style: const TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                              color: AppColors.textSecondary),
                        ),
                      );
                    }).toList(),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  void _handleMenuAction(String value, Exercise exercise) {
    switch (value) {
      case 'details':
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => ExerciseDetailsScreen(
              exercise: exercise,
              plan: widget.plan,
            ),
          ),
        );
        break;
      case 'edit':
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => Saveexercise(
              plan: widget.plan,
              exercise: exercise,
            ),
          ),
        );
        break;
      case 'delete':
        _showDeleteConfirm(exercise);
        break;
    }
  }

  void _showDeleteConfirm(Exercise exercise) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text("Delete Exercise?"),
        content: Text("Are you sure you want to remove '${exercise.name}'?"),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text("Cancel")),
          TextButton(
            onPressed: () {
              Provider.of<Exerciseprovider>(context, listen: false)
                  .deleteExercise(exercise.id!, widget.plan.planId!);
              Navigator.pop(context);
            },
            child: const Text("Delete", style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
  }
}
