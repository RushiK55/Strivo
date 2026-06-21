import 'package:flutter/material.dart';
import 'package:strivo/models/Exercise.dart';
import 'package:strivo/models/Plan.dart';
import 'package:strivo/Screens/WorkoutPlanPage.dart';

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
      backgroundColor: const Color(0xFFF8F9FE),
      body: CustomScrollView(
        slivers: [
          SliverAppBar(
            expandedHeight: 250,
            pinned: true,
            flexibleSpace: FlexibleSpaceBar(
              title: Text(
                exercise.name,
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.bold,
                  shadows: [Shadow(color: Colors.black45, blurRadius: 10)],
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
                  child: Icon(
                    Icons.fitness_center,
                    size: 80,
                    color: Colors.white.withOpacity(0.3),
                  ),
                ),
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildSectionTitle("Summary"),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      _buildStatCard(
                        "Sets",
                        exercise.sets,
                        Icons.layers_outlined,
                        Colors.orange,
                      ),
                      const SizedBox(width: 12),
                      _buildStatCard(
                        "Reps",
                        exercise.reps,
                        Icons.repeat,
                        Colors.blue,
                      ),
                      const SizedBox(width: 12),
                      _buildStatCard(
                        "Weight",
                        "${exercise.weight} kg",
                        Icons.fitness_center,
                        Colors.green,
                      ),
                    ],
                  ),
                  const SizedBox(height: 32),
                  if (exercise.notes.isNotEmpty) ...[
                    _buildSectionTitle("Notes"),
                    const SizedBox(height: 12),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(15),
                        border: Border.all(color: Colors.grey.withOpacity(0.1)),
                      ),
                      child: Text(
                        exercise.notes,
                        style: TextStyle(
                          color: Colors.grey[700],
                          height: 1.5,
                          fontSize: 16,
                        ),
                      ),
                    ),
                    const SizedBox(height: 32),
                  ],
                  _buildSectionTitle("Target Plan"),
                  const SizedBox(height: 12),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: Colors.deepPurple.withOpacity(0.1),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.assignment_outlined, color: Colors.deepPurple),
                    ),
                    title: Text(
                      plan.planName,
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                    subtitle: Text(plan.planDay),
                  ),
                  const SizedBox(height: 32),
                  if (exercise.setsList.isNotEmpty) ...[
                    _buildSectionTitle(exercise.isCheck ? "Performance Details" : "Planned Sets"),
                    const SizedBox(height: 12),
                    ListView.builder(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      itemCount: exercise.setsList.length,
                      itemBuilder: (context, index) {
                        final set = exercise.setsList[index];
                        return Container(
                          margin: const EdgeInsets.only(bottom: 12),
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(15),
                            border: Border.all(color: Colors.grey.withOpacity(0.05)),
                          ),
                          child: Row(
                            children: [
                              CircleAvatar(
                                radius: 12,
                                backgroundColor: set.isCompleted ? Colors.green : Colors.grey[200],
                                child: Text(
                                  "${index + 1}",
                                  style: TextStyle(
                                    fontSize: 10,
                                    color: set.isCompleted ? Colors.white : Colors.black54,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ),
                              const SizedBox(width: 16),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      "${set.weight} kg × ${set.reps} reps",
                                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                                    ),
                                    if (set.setDuration.isNotEmpty && set.setDuration != "00:00:00")
                                      Text(
                                        "Duration: ${set.setDuration}",
                                        style: TextStyle(color: Colors.grey[500], fontSize: 12),
                                      ),
                                  ],
                                ),
                              ),
                              if (set.isCompleted)
                                const Icon(Icons.check_circle, color: Colors.green, size: 20),
                            ],
                          ),
                        );
                      },
                    ),
                  ],
                  const SizedBox(height: 40),
                  SizedBox(
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
                      icon: Icon(exercise.isCheck ? Icons.refresh_rounded : Icons.play_arrow_rounded),
                      label: Text(
                        exercise.isCheck ? "RESTART WORKOUT SESSION" : "START WORKOUT SESSION",
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                      ),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: exercise.isCheck ? Colors.white : const Color(0xFFD0FD3E),
                        foregroundColor: Colors.black,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(18),
                          side: exercise.isCheck ? const BorderSide(color: Colors.black12) : BorderSide.none,
                        ),
                        elevation: 0,
                      ),
                    ),
                  ),
                  const SizedBox(height: 100),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Text(
      title.toUpperCase(),
      style: TextStyle(
        fontSize: 13,
        fontWeight: FontWeight.bold,
        color: Colors.grey[500],
        letterSpacing: 1.2,
      ),
    );
  }

  Widget _buildStatCard(String label, String value, IconData icon, Color color) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(18),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.02),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          children: [
            Icon(icon, color: color, size: 24),
            const SizedBox(height: 8),
            Text(
              value,
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              label,
              style: TextStyle(
                fontSize: 12,
                color: Colors.grey[500],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
