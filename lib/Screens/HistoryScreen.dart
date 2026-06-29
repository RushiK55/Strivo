import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:strivo/models/workout_session.dart';
import 'package:strivo/providers/ExerciseProvider.dart';
import 'package:strivo/utils/app_colors.dart';
import 'HistoryDetailsScreen.dart';

class HistoryScreen extends StatefulWidget {
  const HistoryScreen({super.key});

  @override
  State<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends State<HistoryScreen> {
  @override
  void initState() {
    super.initState();
    Future.microtask(() =>
        Provider.of<Exerciseprovider>(context, listen: false).fetchHistory());
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text("Workout History",
            style: TextStyle(fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
        centerTitle: true,
        elevation: 0,
        backgroundColor: AppColors.background,
        foregroundColor: AppColors.textPrimary,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 20),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: Consumer<Exerciseprovider>(
        builder: (context, provider, child) {
          if (provider.history.isEmpty) {
            return _buildEmptyState();
          }

          // Group sessions by date for display
          Map<String, List<WorkoutSession>> groupedHistory = {};
          for (var session in provider.history) {
            String dateKey = DateFormat('yyyy-MM-dd').format(session.date);
            if (groupedHistory[dateKey] == null) {
              groupedHistory[dateKey] = [];
            }
            groupedHistory[dateKey]!.add(session);
          }

          return ListView.builder(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            itemCount: groupedHistory.keys.length,
            itemBuilder: (context, index) {
              String dateKey = groupedHistory.keys.elementAt(index);
              List<WorkoutSession> sessions = groupedHistory[dateKey]!;
              DateTime date = DateTime.parse(dateKey);

              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding:
                        const EdgeInsets.symmetric(vertical: 16, horizontal: 8),
                    child: Row(
                      children: [
                        Text(
                          _getFormattedDate(date),
                          style: const TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                            color: AppColors.textSecondary,
                            letterSpacing: 1.0,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(child: Divider(color: AppColors.surface, thickness: 1)),
                      ],
                    ),
                  ),
                  ...sessions.map((s) => _buildHistoryCard(s)).toList(),
                  const SizedBox(height: 10),
                ],
              );
            },
          );
        },
      ),
    );
  }

  String _getFormattedDate(DateTime date) {
    DateTime now = DateTime.now();
    DateTime yesterday = DateTime.now().subtract(const Duration(days: 1));

    if (DateFormat('yyyy-MM-dd').format(date) ==
        DateFormat('yyyy-MM-dd').format(now)) {
      return "TODAY";
    } else if (DateFormat('yyyy-MM-dd').format(date) ==
        DateFormat('yyyy-MM-dd').format(yesterday)) {
      return "YESTERDAY";
    } else {
      return DateFormat('EEEE, d MMM').format(date).toUpperCase();
    }
  }

  Widget _buildEmptyState() {
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
            child: Icon(Icons.history_rounded, size: 60, color: AppColors.accent.withOpacity(0.5)),
          ),
          const SizedBox(height: 30),
          const Text("No history found",
              style: TextStyle(
                  fontSize: 22,
                  color: AppColors.textPrimary,
                  fontWeight: FontWeight.w900,
                  letterSpacing: 1.0)),
          const SizedBox(height: 12),
          const Text("Your workout legacy starts here.",
              style: TextStyle(color: AppColors.textSecondary, fontSize: 15)),
        ],
      ),
    );
  }

  Widget _buildHistoryCard(WorkoutSession session) {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(30),
        border: Border.all(color: const Color(0xFF2C2C2E)),
      ),
      child: InkWell(
        onTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => HistoryDetailsScreen(session: session),
            ),
          );
        },
        borderRadius: BorderRadius.circular(30),
        child: Padding(
          padding: const EdgeInsets.all(20.0),
          child: Row(
            children: [
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AppColors.accent.withOpacity(0.1),
                  shape: BoxShape.circle,
                ),
                child: const Icon(Icons.fitness_center_rounded,
                    color: AppColors.accent, size: 24),
              ),
              const SizedBox(width: 20),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      session.planName,
                      style: const TextStyle(
                          fontSize: 20, fontWeight: FontWeight.w900, color: AppColors.textPrimary, letterSpacing: 0.5),
                    ),
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        const Icon(Icons.timer_outlined, size: 12, color: AppColors.textSecondary),
                        const SizedBox(width: 4),
                        Text(
                          session.totalTime,
                          style: const TextStyle(color: AppColors.textSecondary, fontSize: 12, fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(width: 12),
                        const Icon(Icons.bolt_rounded, size: 12, color: AppColors.accent),
                        const SizedBox(width: 4),
                        Text(
                          "${session.exercises.length} EXERCISES",
                          style: const TextStyle(color: AppColors.textSecondary, fontSize: 11, fontWeight: FontWeight.w900, letterSpacing: 0.5),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    DateFormat('h:mm a').format(session.date),
                    style: const TextStyle(color: AppColors.textSecondary, fontSize: 12, fontWeight: FontWeight.w500),
                  ),
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.all(4),
                    decoration: BoxDecoration(
                      color: const Color(0xFF2C2C2E),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.arrow_forward_ios_rounded, size: 12, color: AppColors.textPrimary),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
