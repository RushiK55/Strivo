import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:strivo/Screens/SaveExercise.dart';
import 'package:strivo/Screens/WorkoutPlanPage.dart';
import 'package:strivo/Screens/ExerciseDetailsScreen.dart';
import 'package:strivo/models/Plan.dart';
import 'package:strivo/providers/ExerciseProvider.dart';

class ExerciseItem extends StatelessWidget {
  final String name;
  final String weight;
  final String sets;
  final String reps;
  final String notes;
  final bool isChecked;
  final VoidCallback onDelete;
  final VoidCallback onToggle;
  final VoidCallback onTap;

  ExerciseItem({
    required this.name,
    required this.weight,
    required this.sets,
    required this.reps,
    required this.notes,
    required this.isChecked,
    required this.onDelete,
    required this.onToggle,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Container(
        margin: EdgeInsets.symmetric(vertical: 8.0, horizontal: 16.0),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(15.0),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.05),
              blurRadius: 10,
              offset: Offset(0, 4),
            ),
          ],
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(15.0),
          child: IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Container(
                  width: 6,
                  color: isChecked ? Colors.green : Colors.deepPurple,
                ),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Expanded(
                              child: Text(
                                name,
                                style: TextStyle(
                                  fontSize: 18,
                                  fontWeight: FontWeight.bold,
                                  decoration: isChecked ? TextDecoration.lineThrough : null,
                                  color: isChecked ? Colors.grey : Colors.black87,
                                ),
                              ),
                            ),
                            IconButton(
                              icon: Icon(Icons.delete_outline, color: Colors.red[300], size: 20),
                              onPressed: onDelete,
                            ),
                          ],
                        ),
                        SizedBox(height: 8),
                        Row(
                          children: [
                            _buildInfoChip(Icons.fitness_center, "$weight kg", Colors.blue[50]!, Colors.blue),
                            SizedBox(width: 8),
                            _buildInfoChip(Icons.repeat, "$sets x $reps", Colors.orange[50]!, Colors.orange),
                          ],
                        ),
                        if (notes.isNotEmpty) ...[
                          SizedBox(height: 12),
                          Text(
                            notes,
                            style: TextStyle(
                              fontSize: 13,
                              color: Colors.grey[600],
                              fontStyle: FontStyle.italic,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
                InkWell(
                  onTap: onToggle,
                  child: Container(
                    width: 60,
                    color: isChecked ? Colors.green[50] : Colors.grey[50],
                    child: Center(
                      child: Icon(
                        isChecked ? Icons.check_circle : Icons.radio_button_unchecked,
                        color: isChecked ? Colors.green : Colors.grey[400],
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildInfoChip(IconData icon, String label, Color bgColor, Color iconColor) {
    return Container(
      padding: EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: iconColor),
          SizedBox(width: 4),
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: iconColor.withOpacity(0.8),
            ),
          ),
        ],
      ),
    );
  }
}

class Exerciselist extends StatefulWidget {
  final Plan plan;

  const Exerciselist({super.key, required this.plan});

  @override
  State<Exerciselist> createState() => _ExerciselistState();
}

class _ExerciselistState extends State<Exerciselist> {
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

    return Scaffold(
      backgroundColor: Colors.grey[50],
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(widget.plan.planName),
            Text(
              widget.plan.planDay,
              style: TextStyle(fontSize: 12, fontWeight: FontWeight.normal),
            ),
          ],
        ),
        elevation: 0,
        backgroundColor: Colors.white,
        foregroundColor: Colors.black,
      ),
      body: exerciseProvider.exercises.isEmpty
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.list_alt, size: 80, color: Colors.grey[300]),
                  SizedBox(height: 16),
                  Text(
                    "No exercises added yet",
                    style: TextStyle(color: Colors.grey, fontSize: 16),
                  ),
                ],
              ),
            )
          : ListView.builder(
              padding: EdgeInsets.only(top: 10, bottom: 80),
              itemCount: exerciseProvider.exercises.length,
              itemBuilder: (context, index) {
                final exercise = exerciseProvider.exercises[index];
                return ExerciseItem(
                  name: exercise.name,
                  weight: exercise.weight,
                  sets: exercise.sets,
                  reps: exercise.reps,
                  notes: exercise.notes,
                  isChecked: exercise.isCheck,
                  onDelete: () {
                    _showDeleteConfirm(context, exerciseProvider, exercise);
                  },
                  onToggle: () {
                    exercise.isCheck = !exercise.isCheck;
                    exerciseProvider.updateExercise(exercise);
                  },
                  onTap: () {
                    if (exercise.isCheck) {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => ExerciseDetailsScreen(
                            exercise: exercise,
                            plan: widget.plan,
                          ),
                        ),
                      );
                    } else {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => WorkoutPlanPage(
                            plan: widget.plan,
                            initialExerciseIndex: index,
                          ),
                        ),
                      );
                    }
                  },
                );
              },
            ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => Saveexercise(plan: widget.plan),
            ),
          );
        },
        label: Text("Add Exercise"),
        icon: Icon(Icons.add),
        backgroundColor: Colors.deepPurple,
      ),
    );
  }

  void _showDeleteConfirm(BuildContext context, Exerciseprovider provider, dynamic exercise) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text("Delete Exercise?"),
        content: Text("Are you sure you want to remove this exercise from the plan?"),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: Text("Cancel")),
          TextButton(
            onPressed: () {
              provider.deleteExercise(exercise.id!, widget.plan.planId!);
              Navigator.pop(context);
            },
            child: Text("Delete", style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
  }
}
