import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:strivo/models/Exercise.dart';
import 'package:strivo/models/Plan.dart';
import 'package:strivo/providers/ExerciseProvider.dart';
import '../widgets/wheel_picker.dart';
import 'package:strivo/utils/app_colors.dart';

class Saveexercise extends StatefulWidget {
  final Plan? plan; // Optional for extra exercises
  final Exercise? exercise;
  final bool isExtra;

  const Saveexercise({
    super.key,
    this.plan,
    this.exercise,
    this.isExtra = false,
  });

  @override
  State<Saveexercise> createState() => _SaveexerciseState();
}

class _SaveexerciseState extends State<Saveexercise> {
  final _formKey = GlobalKey<FormState>();
  final TextEditingController _exerciseNameController = TextEditingController();
  final TextEditingController _notesController = TextEditingController();

  int _sets = 3;
  int _reps = 10;
  int _weightInt = 40;
  int _weightDec = 0;

  bool _vanishEndOfDay = false;

  @override
  void initState() {
    super.initState();
    if (widget.exercise != null) {
      _exerciseNameController.text = widget.exercise!.name;
      _notesController.text = widget.exercise!.notes;
      _vanishEndOfDay = widget.exercise!.vanishEndOfDay;
      
      _sets = int.tryParse(widget.exercise!.sets) ?? 3;
      _reps = int.tryParse(widget.exercise!.reps) ?? 10;
      double w = double.tryParse(widget.exercise!.weight) ?? 40.0;
      _weightInt = w.floor();
      _weightDec = ((w - _weightInt) * 10).round();
    }
  }

  @override
  void dispose() {
    _exerciseNameController.dispose();
    _notesController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final exerciseProvider = Provider.of<Exerciseprovider>(context);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: Text(
            widget.exercise == null
                ? (widget.isExtra ? "Extra Workout" : "New Exercise")
                : "Edit Exercise",
            style: const TextStyle(fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
        centerTitle: true,
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
        padding: const EdgeInsets.symmetric(horizontal: 24.0),
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 20),
              _buildSectionTitle("Exercise Detail"),
              _buildNameField(),
              
              const SizedBox(height: 30),
              _buildSectionTitle("Target Metrics"),
              _buildTargetMetricsPickers(),
              
              if (widget.isExtra) ...[
                const SizedBox(height: 30),
                _buildSectionTitle("Options"),
                _buildVanishToggle(),
              ],
              
              const SizedBox(height: 30),
              _buildSectionTitle("Extra Notes"),
              _buildNotesField(),
              
              const SizedBox(height: 60),
              _buildSaveButton(exerciseProvider),
              const SizedBox(height: 30),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTargetMetricsPickers() {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 16),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(25),
        border: Border.all(color: const Color(0xFF2C2C2E)),
      ),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            WheelPicker(
              label: "SETS",
              minValue: 1,
              maxValue: 20,
              initialValue: _sets,
              onChanged: (val) => setState(() => _sets = val),
              width: 65,
            ),
            const SizedBox(width: 15),
            WheelPicker(
              label: "REPS",
              minValue: 1,
              maxValue: 100,
              initialValue: _reps,
              onChanged: (val) => setState(() => _reps = val),
              width: 65,
            ),
            const SizedBox(width: 15),
            Row(
              children: [
                WheelPicker(
                  label: "WEIGHT (KG)",
                  minValue: 0,
                  maxValue: 500,
                  initialValue: _weightInt,
                  onChanged: (val) => setState(() => _weightInt = val),
                  width: 75,
                ),
                const Padding(
                  padding: EdgeInsets.only(top: 40),
                  child: Text(".",
                      style: TextStyle(
                          fontSize: 30,
                          fontWeight: FontWeight.bold,
                          color: AppColors.textPrimary)),
                ),
                WheelPicker(
                  label: "",
                  minValue: 0,
                  maxValue: 9,
                  initialValue: _weightDec,
                  onChanged: (val) => setState(() => _weightDec = val),
                  width: 55,
                ),
              ],
            ),
          ],
        ),
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
        const SizedBox(height: 16),
      ],
    );
  }

  Widget _buildNameField() {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: const Color(0xFF2C2C2E)),
      ),
      child: TextFormField(
        controller: _exerciseNameController,
        style: const TextStyle(
            fontWeight: FontWeight.bold, fontSize: 18, color: AppColors.textPrimary),
        decoration: InputDecoration(
          hintText: "e.g. Bench Press",
          hintStyle: const TextStyle(
              color: Color(0xFF5E5E5E), fontWeight: FontWeight.normal, fontSize: 16),
          prefixIcon: Container(
            margin: const EdgeInsets.all(12),
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: AppColors.accent.withOpacity(0.1),
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.fitness_center_rounded, color: AppColors.accent, size: 20),
          ),
          border: InputBorder.none,
          contentPadding:
              const EdgeInsets.symmetric(horizontal: 20, vertical: 20),
        ),
        validator: (value) => value == null || value.isEmpty ? 'Required' : null,
      ),
    );
  }

  Widget _buildVanishToggle() {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(25),
        border: Border.all(color: const Color(0xFF2C2C2E)),
      ),
      child: SwitchListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
        title: const Text("Vanish at end of day",
            style: TextStyle(fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
        subtitle: const Text("Keep your dashboard clean tomorrow",
            style: TextStyle(color: AppColors.textSecondary, fontSize: 13)),
        value: _vanishEndOfDay,
        onChanged: (val) => setState(() => _vanishEndOfDay = val),
        activeColor: AppColors.accent,
        activeTrackColor: AppColors.accent.withOpacity(0.3),
      ),
    );
  }

  Widget _buildNotesField() {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(25),
        border: Border.all(color: const Color(0xFF2C2C2E)),
      ),
      child: TextFormField(
        controller: _notesController,
        maxLines: 4,
        style: const TextStyle(fontSize: 16, color: AppColors.textPrimary),
        decoration: const InputDecoration(
          hintText: "Add specific training tips or cues...",
          hintStyle: TextStyle(color: Color(0xFF5E5E5E), fontSize: 15),
          border: InputBorder.none,
          contentPadding: EdgeInsets.all(24),
        ),
      ),
    );
  }

  Widget _buildSaveButton(Exerciseprovider provider) {
    return SizedBox(
      width: double.infinity,
      height: 60,
      child: ElevatedButton(
        onPressed: () async {
          if (_formKey.currentState!.validate()) {
            double finalWeight = _weightInt + (_weightDec / 10.0);
            
            if (widget.exercise == null) {
              await provider.addExercise(
                Exercise(
                  planId: widget.plan?.planId ?? -1,
                  name: _exerciseNameController.text,
                  weight: finalWeight.toString(),
                  sets: _sets.toString(),
                  reps: _reps.toString(),
                  notes: _notesController.text,
                  isExtra: widget.isExtra,
                  vanishEndOfDay: _vanishEndOfDay,
                  setsList: [],
                ),
              );
            } else {
              final e = widget.exercise!;
              e.name = _exerciseNameController.text;
              e.notes = _notesController.text;
              e.vanishEndOfDay = _vanishEndOfDay;
              e.sets = _sets.toString();
              e.reps = _reps.toString();
              e.weight = finalWeight.toString();
              await provider.updateExercise(e);
            }
            if (mounted) Navigator.pop(context);
          }
        },
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.accent,
          foregroundColor: Colors.black,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          elevation: 0,
        ),
        child: Text(
          widget.exercise == null ? "FINISH & SAVE" : "UPDATE EXERCISE",
          style: const TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w900,
              letterSpacing: 1.2),
        ),
      ),
    );
  }
}
