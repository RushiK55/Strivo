import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:strivo/Screens/ExerciseList.dart';
import 'package:strivo/models/Plan.dart';
import 'package:strivo/providers/PlanProvider.dart';

class SavePlan extends StatefulWidget {
  final String? initialDay;
  const SavePlan({super.key, this.initialDay});

  @override
  State<SavePlan> createState() => _SavePlanState();
}

class _SavePlanState extends State<SavePlan> {
  final _formKey = GlobalKey<FormState>();
  final TextEditingController _planNameController = TextEditingController();
  late String _selectedDay;

  final List<String> _days = [
    'Monday',
    'Tuesday',
    'Wednesday',
    'Thursday',
    'Friday',
    'Saturday',
    'Sunday'
  ];

  @override
  void initState() {
    super.initState();
    _selectedDay = widget.initialDay ?? 'Monday';
  }

  @override
  void dispose() {
    _planNameController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final planProvider = Provider.of<Planprovider>(context);
    final groupedPlans = planProvider.plansByDay;
    final plansForSelectedDay = groupedPlans[_selectedDay] ?? [];

    return Scaffold(
      appBar: AppBar(
        title: const Text("Create New Plan"),
        elevation: 0,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20.0),
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                "Plan Details",
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.bold,
                  color: Theme.of(context).primaryColor,
                ),
              ),
              const SizedBox(height: 20),
              TextFormField(
                controller: _planNameController,
                decoration: InputDecoration(
                  labelText: 'Plan Name',
                  hintText: 'e.g. Chest & Triceps',
                  prefixIcon: const Icon(Icons.fitness_center),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                  filled: true,
                  fillColor: Colors.grey[50],
                ),
                validator: (value) {
                  if (value == null || value.isEmpty) {
                    return 'Please enter a plan name';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 25),
              Text(
                "Select Day",
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                  color: Colors.grey[700],
                ),
              ),
              const SizedBox(height: 10),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.grey),
                  color: Colors.grey[50],
                ),
                child: DropdownButtonHideUnderline(
                  child: DropdownButton<String>(
                    isExpanded: true,
                    value: _selectedDay,
                    items: _days.map((String day) {
                      final hasPlan = (groupedPlans[day] ?? []).isNotEmpty;
                      return DropdownMenuItem<String>(
                        value: day,
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(day),
                            if (hasPlan)
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                decoration: BoxDecoration(
                                  color: Colors.orange[50],
                                  borderRadius: BorderRadius.circular(10),
                                  border: Border.all(color: Colors.orange[200]!),
                                ),
                                child: Text(
                                  "${groupedPlans[day]!.length} Plan(s)",
                                  style: TextStyle(fontSize: 10, color: Colors.orange[800]),
                                ),
                              ),
                          ],
                        ),
                      );
                    }).toList(),
                    onChanged: (String? newValue) {
                      setState(() {
                        _selectedDay = newValue!;
                      });
                    },
                  ),
                ),
              ),
              if (plansForSelectedDay.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 12, left: 4),
                  child: Row(
                    children: [
                      const Icon(Icons.info_outline, size: 16, color: Colors.orange),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          "You already have ${plansForSelectedDay.length} plan(s) for $_selectedDay. You can still add another.",
                          style: TextStyle(color: Colors.orange[800], fontSize: 13),
                        ),
                      ),
                    ],
                  ),
                ),
              const SizedBox(height: 40),
              ElevatedButton(
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                  elevation: 2,
                ),
                onPressed: () async {
                  if (_formKey.currentState!.validate()) {
                    int id = await planProvider.addPlan(
                      Plan(
                        planName: _planNameController.text,
                        planDay: _selectedDay,
                      ),
                    );
                    if (mounted) {
                      savePlan(id, _planNameController.text, _selectedDay);
                    }
                  }
                },
                child: const Center(
                  child: Text(
                    "Save Plan",
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void savePlan(int id, String planName, String day) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text("Plan Saved"),
        content: Text("Plan '$planName' has been added to $day."),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.pop(context); // Close dialog
              Navigator.pushReplacement(
                context,
                MaterialPageRoute(
                  builder: (context) => Exerciselist(
                    plan: Plan(planId: id, planName: planName, planDay: day),
                  ),
                ),
              );
            },
            child: const Text("OK"),
          ),
        ],
      ),
    );
  }
}
