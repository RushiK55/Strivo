import 'package:flutter/widgets.dart';
import '../models/Plan.dart';
import '../database/database_helper.dart';

class Planprovider extends ChangeNotifier {
  List<Plan> _plans = [];

  List<Plan> get plans => _plans;

  Map<String, List<Plan>> get plansByDay {
    Map<String, List<Plan>> map = {};
    for (var plan in _plans) {
      map.putIfAbsent(plan.planDay, () => []).add(plan);
    }
    return map;
  }

  Future<void> refreshPlans() async {
    _plans = await DatabaseHelper.instance.readAllPlans();
    notifyListeners();
  }

  Future<int> addPlan(Plan p) async {
    int id = await DatabaseHelper.instance.createPlan(p);
    await refreshPlans();
    return id;
  }

  Future<void> deletePlan(int id) async {
    await DatabaseHelper.instance.deletePlan(id);
    await refreshPlans();
  }
}
