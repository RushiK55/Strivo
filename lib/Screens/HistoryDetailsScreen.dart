import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../models/Exercise.dart';

class HistoryDetailsScreen extends StatelessWidget {
  final Map<String, dynamic> historyItem;

  const HistoryDetailsScreen({super.key, required this.historyItem});

  @override
  Widget build(BuildContext context) {
    // Reconstruct exercise to use its helper methods/decoding logic
    final exercise = Exercise.fromMap(historyItem);
    final DateTime completedAt = DateTime.parse(historyItem['completedAt']);
    
    double totalVolume = exercise.setsList.fold(0, (sum, s) => sum + (s.weight * s.reps));

    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FE),
      appBar: AppBar(
        title: const Text("Workout Details", style: TextStyle(fontWeight: FontWeight.bold)),
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildHeader(exercise, completedAt),
            const SizedBox(height: 24),
            _buildSummaryCards(exercise, totalVolume),
            const SizedBox(height: 30),
            const Text(
              "COMPLETED SETS",
              style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Colors.grey, letterSpacing: 1.2),
            ),
            const SizedBox(height: 12),
            _buildSetsList(exercise),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(Exercise exercise, DateTime date) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.03), blurRadius: 10)],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            exercise.name,
            style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Icon(Icons.calendar_today, size: 14, color: Colors.grey[600]),
              const SizedBox(width: 6),
              Text(
                DateFormat('EEEE, d MMMM yyyy • h:mm a').format(date),
                style: TextStyle(color: Colors.grey[600], fontSize: 14),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildSummaryCards(Exercise exercise, double volume) {
    return Row(
      children: [
        _buildStatCard("Sets", exercise.setsList.length.toString(), Icons.layers_outlined, Colors.blue),
        const SizedBox(width: 12),
        _buildStatCard("Volume", "${volume.toStringAsFixed(1)} kg", Icons.fitness_center, Colors.orange),
      ],
    );
  }

  Widget _buildStatCard(String label, String value, IconData icon, Color color) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: color, size: 20),
            const SizedBox(height: 12),
            Text(label, style: const TextStyle(color: Colors.grey, fontSize: 12, fontWeight: FontWeight.bold)),
            const SizedBox(height: 4),
            Text(value, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          ],
        ),
      ),
    );
  }

  Widget _buildSetsList(Exercise exercise) {
    return ListView.builder(
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
            borderRadius: BorderRadius.circular(16),
          ),
          child: Row(
            children: [
              CircleAvatar(
                radius: 14,
                backgroundColor: const Color(0xFFD0FD3E),
                child: Text("${index + 1}", style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Colors.black)),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text("${set.weight} kg × ${set.reps} reps", style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                    if (set.restTime.isNotEmpty && set.restTime != "00:00:00")
                      Padding(
                        padding: const EdgeInsets.only(top: 4),
                        child: Text("Rest: ${set.restTime}", style: TextStyle(color: Colors.grey[500], fontSize: 12)),
                      ),
                  ],
                ),
              ),
              const Icon(Icons.check_circle, color: Colors.green, size: 20),
            ],
          ),
        );
      },
    );
  }
}
