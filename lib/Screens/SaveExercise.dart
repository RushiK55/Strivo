import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:strivo/models/Exercise.dart';
import 'package:strivo/models/Plan.dart';
import 'package:strivo/providers/ExerciseProvider.dart';
import '../widgets/wheel_picker.dart';

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

  bool _vanishEndOfDay = false;

  @override
  void initState() {
    super.initState();
    if (widget.exercise != null) {
      _exerciseNameController.text = widget.exercise!.name;
      _notesController.text = widget.exercise!.notes;
      _vanishEndOfDay = widget.exercise!.vanishEndOfDay;
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
      backgroundColor: const Color(0xFFF8F9FE),
      appBar: AppBar(
        title: Text(
          widget.exercise == null 
            ? (widget.isExtra ? "Extra Workout" : "New Exercise") 
            : "Edit Exercise", 
          style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 22)
        ),
        centerTitle: true,
        elevation: 0,
        backgroundColor: Colors.transparent,
        foregroundColor: Colors.black,
      ),
      body: SingleChildScrollView(
        physics: const BouncingScrollPhysics(),
        padding: const EdgeInsets.symmetric(horizontal: 20.0),
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 10),
              _buildSectionTitle("Exercise Detail"),
              _buildNameField(),
              
              if (widget.isExtra) ...[
                const SizedBox(height: 30),
                _buildSectionTitle("Options"),
                _buildVanishToggle(),
              ],
              
              const SizedBox(height: 30),
              _buildSectionTitle("Extra Notes"),
              _buildNotesField(),
              
              const SizedBox(height: 40),
              _buildSaveButton(exerciseProvider),
              const SizedBox(height: 30),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.only(left: 4, bottom: 12),
      child: Text(
        title.toUpperCase(),
        style: TextStyle(
          fontSize: 13,
          fontWeight: FontWeight.bold,
          color: Colors.grey[600],
          letterSpacing: 1.2,
        ),
      ),
    );
  }

  Widget _buildNameField() {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(color: Colors.black.withOpacity(0.04), blurRadius: 15, offset: const Offset(0, 8)),
        ],
      ),
      child: TextFormField(
        controller: _exerciseNameController,
        style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16),
        decoration: InputDecoration(
          hintText: "Enter exercise name...",
          hintStyle: TextStyle(color: Colors.grey[400], fontWeight: FontWeight.normal),
          prefixIcon: const Icon(Icons.fitness_center, color: Colors.deepPurple),
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
        ),
        validator: (value) => value == null || value.isEmpty ? 'Required' : null,
      ),
    );
  }

  Widget _buildVanishToggle() {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(color: Colors.black.withOpacity(0.04), blurRadius: 15, offset: const Offset(0, 8)),
        ],
      ),
      child: SwitchListTile(
        title: const Text("Vanish at end of day", style: TextStyle(fontWeight: FontWeight.w600)),
        subtitle: const Text("Keep your dashboard clean tomorrow"),
        value: _vanishEndOfDay,
        onChanged: (val) => setState(() => _vanishEndOfDay = val),
        activeColor: Colors.deepPurple,
      ),
    );
  }

  Widget _buildNotesField() {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(color: Colors.black.withOpacity(0.04), blurRadius: 15, offset: const Offset(0, 8)),
        ],
      ),
      child: TextFormField(
        controller: _notesController,
        maxLines: 4,
        style: const TextStyle(fontSize: 15),
        decoration: InputDecoration(
          hintText: "Add training notes here...",
          hintStyle: TextStyle(color: Colors.grey[400]),
          border: InputBorder.none,
          contentPadding: const EdgeInsets.all(20),
        ),
      ),
    );
  }

  Widget _buildSaveButton(Exerciseprovider provider) {
    return Container(
      width: double.infinity,
      height: 65,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        gradient: const LinearGradient(
          colors: [Color(0xFF673AB7), Color(0xFF512DA8)],
        ),
        boxShadow: [
          BoxShadow(color: Colors.deepPurple.withOpacity(0.3), blurRadius: 12, offset: const Offset(0, 6)),
        ],
      ),
      child: ElevatedButton(
        onPressed: () async {
          if (_formKey.currentState!.validate()) {
            if (widget.exercise == null) {
              await provider.addExercise(
                Exercise(
                  planId: widget.plan?.planId ?? -1,
                  name: _exerciseNameController.text,
                  weight: "0",
                  sets: "0",
                  reps: "0",
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
              await provider.updateExercise(e);
            }
            if (mounted) Navigator.pop(context);
          }
        },
        style: ElevatedButton.styleFrom(
          backgroundColor: Colors.transparent,
          shadowColor: Colors.transparent,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        ),
        child: Text(
          widget.exercise == null ? "FINISH & SAVE" : "UPDATE EXERCISE",
          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800, color: Colors.white, letterSpacing: 1.5),
        ),
      ),
    );
  }
}
