import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'HistoryDetailsScreen.dart';
import '../database/database_helper.dart';

class HistoryScreen extends StatefulWidget {
  const HistoryScreen({super.key});

  @override
  State<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends State<HistoryScreen> {
  Map<String, List<Map<String, dynamic>>> _groupedHistory = {};
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  Future<void> _loadHistory() async {
    final history = await DatabaseHelper.instance.getHistory();
    Map<String, List<Map<String, dynamic>>> grouped = {};

    for (var item in history) {
      DateTime date = DateTime.parse(item['completedAt']);
      String dateKey = DateFormat('yyyy-MM-dd').format(date);
      if (grouped[dateKey] == null) {
        grouped[dateKey] = [];
      }
      grouped[dateKey]!.add(item);
    }

    setState(() {
      _groupedHistory = grouped;
      _isLoading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FE),
      appBar: AppBar(
        title: const Text("Workout History", style: TextStyle(fontWeight: FontWeight.bold)),
        centerTitle: true,
        elevation: 0,
        backgroundColor: Colors.transparent,
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _groupedHistory.isEmpty
              ? _buildEmptyState()
              : ListView.builder(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                  itemCount: _groupedHistory.keys.length,
                  itemBuilder: (context, index) {
                    String dateKey = _groupedHistory.keys.elementAt(index);
                    List<Map<String, dynamic>> exercises = _groupedHistory[dateKey]!;
                    DateTime date = DateTime.parse(dateKey);

                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
                          child: Text(
                            _getFormattedDate(date),
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.bold,
                              color: Colors.grey[600],
                              letterSpacing: 0.5,
                            ),
                          ),
                        ),
                        ...exercises.map((e) => _buildHistoryCard(e)).toList(),
                        const SizedBox(height: 10),
                      ],
                    );
                  },
                ),
    );
  }

  String _getFormattedDate(DateTime date) {
    DateTime now = DateTime.now();
    DateTime yesterday = DateTime.now().subtract(const Duration(days: 1));

    if (DateFormat('yyyy-MM-dd').format(date) == DateFormat('yyyy-MM-dd').format(now)) {
      return "TODAY";
    } else if (DateFormat('yyyy-MM-dd').format(date) == DateFormat('yyyy-MM-dd').format(yesterday)) {
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
          Icon(Icons.history, size: 80, color: Colors.grey[300]),
          const SizedBox(height: 16),
          Text("No history yet", style: TextStyle(fontSize: 18, color: Colors.grey[600], fontWeight: FontWeight.w500)),
          const SizedBox(height: 8),
          Text("Your completed exercises will appear here", style: TextStyle(color: Colors.grey[400])),
        ],
      ),
    );
  }

  Widget _buildHistoryCard(Map<String, dynamic> item) {
    return InkWell(
      onTap: () {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => HistoryDetailsScreen(historyItem: item),
          ),
        );
      },
      child: Card(
        elevation: 0,
        margin: const EdgeInsets.only(bottom: 10),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(15)),
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Row(
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: Colors.deepPurple.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(Icons.check_circle, color: Colors.deepPurple, size: 24),
              ),
              const SizedBox(width: 15),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      item['name'],
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      "${item['sets']} sets • ${item['reps']} reps • ${item['weight']} kg",
                      style: TextStyle(color: Colors.grey[600], fontSize: 13),
                    ),
                  ],
                ),
              ),
              Text(
                DateFormat('h:mm a').format(DateTime.parse(item['completedAt'])),
                style: TextStyle(color: Colors.grey[400], fontSize: 12),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
