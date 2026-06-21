import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/AuthProvider.dart';
import '../services/user_manager.dart';
import '../widgets/wheel_picker.dart';
import '../database/database_helper.dart';
import 'AuthScreen.dart';
import 'HistoryScreen.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  Map<String, dynamic> _profile = {};
  Map<String, double> _records = {};
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    final profileData = await UserManager.getProfile();
    final recordsData = await DatabaseHelper.instance.getPersonalRecords();
    setState(() {
      _profile = profileData;
      _records = recordsData;
      _isLoading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final authProvider = Provider.of<AuthProvider>(context);

    if (_isLoading) return const Scaffold(body: Center(child: CircularProgressIndicator()));

    return Scaffold(
      appBar: AppBar(
        title: const Text("Profile", style: TextStyle(fontWeight: FontWeight.bold)),
        elevation: 0,
        backgroundColor: Colors.transparent,
      ),
      extendBodyBehindAppBar: true,
      body: SingleChildScrollView(
        child: Column(
          children: [
            Container(
              width: double.infinity,
              padding: const EdgeInsets.only(top: 100, bottom: 40),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [Colors.deepPurple.shade800, Colors.deepPurple.shade400],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: const BorderRadius.only(
                  bottomLeft: Radius.circular(30),
                  bottomRight: Radius.circular(30),
                ),
              ),
              child: Column(
                children: [
                  const CircleAvatar(
                    radius: 50,
                    backgroundColor: Colors.white,
                    child: Icon(Icons.person, size: 60, color: Colors.deepPurple),
                  ),
                  const SizedBox(height: 15),
                  Text(
                    authProvider.userName ?? "User",
                    style: const TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold),
                  ),
                  Text(
                    authProvider.userEmail ?? "",
                    style: const TextStyle(color: Colors.white70, fontSize: 16),
                  ),
                ],
              ),
            ),
            
            Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildSectionTitle("Body Weight Tracker"),
                  _buildWeightStats(),
                  const SizedBox(height: 25),
                  _buildSectionTitle("Personal Records (All-Time)"),
                  _buildPersonalRecords(),
                  const SizedBox(height: 25),
                  _buildSectionTitle("Account Details"),
                  _buildProfileDetails(),
                  const SizedBox(height: 20),
                  _buildSectionTitle("Workout Activity"),
                  _buildListTile(Icons.history, "Workout History", () {
                    Navigator.push(context, MaterialPageRoute(builder: (context) => const HistoryScreen()));
                  }),
                  const SizedBox(height: 10),
                  const Divider(),
                  _buildListTile(Icons.logout, "Logout", () => _showLogoutDialog(context, authProvider), textColor: Colors.red),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.only(left: 4, bottom: 10),
      child: Text(
        title,
        style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: Colors.grey[700], letterSpacing: 0.5),
      ),
    );
  }

  Widget _buildPersonalRecords() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.03), blurRadius: 10)],
      ),
      child: Column(
        children: [
          _recordRow("Highest Lift", "${_records['maxWeight']?.toStringAsFixed(1) ?? '0.0'} kg", Icons.fitness_center),
          const Divider(height: 20),
          _recordRow("Most Reps", "${_records['maxReps']?.toInt() ?? '0'} reps", Icons.repeat),
          const Divider(height: 20),
          _recordRow("Best Volume", "${_records['maxVolume']?.toStringAsFixed(1) ?? '0.0'} kg", Icons.equalizer),
        ],
      ),
    );
  }

  Widget _recordRow(String label, String value, IconData icon) {
    return Row(
      children: [
        Icon(icon, color: Colors.deepPurple, size: 22),
        const SizedBox(width: 15),
        Text(label, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w500)),
        const Spacer(),
        Text(value, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.deepPurple)),
      ],
    );
  }

  Widget _buildWeightStats() {
    return Row(
      children: [
        _statCard("HIGHEST", "${_profile['highWeight']?.toStringAsFixed(1) ?? '--'} kg", Colors.red.shade100, Colors.red),
        const SizedBox(width: 15),
        _statCard("LOWEST", "${_profile['lowWeight']?.toStringAsFixed(1) ?? '--'} kg", Colors.green.shade100, Colors.green),
      ],
    );
  }

  Widget _statCard(String label, String value, Color bg, Color text) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(20)),
        child: Column(
          children: [
            Text(label, style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: text)),
            const SizedBox(height: 4),
            Text(value, style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: text)),
          ],
        ),
      ),
    );
  }

  Widget _buildProfileDetails() {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.03), blurRadius: 10)],
      ),
      child: Column(
        children: [
          _buildDetailRow("Gender", _profile['gender'] ?? "--", Icons.wc, () => _editGender()),
          _buildDetailRow("Age", "${_profile['age'] ?? '--'} yrs", Icons.calendar_today, () => _editAge()),
          _buildDetailRow("Height", "${_profile['height']?.toStringAsFixed(1) ?? '--'} cm", Icons.height, () => _editHeight()),
          _buildDetailRow("Weight", "${_profile['weight']?.toStringAsFixed(1) ?? '--'} kg", Icons.monitor_weight, () => _editWeight()),
        ],
      ),
    );
  }

  Widget _buildDetailRow(String label, String value, IconData icon, VoidCallback onEdit) {
    return ListTile(
      leading: Icon(icon, color: Colors.deepPurple, size: 20),
      title: Text(label, style: const TextStyle(fontSize: 14, color: Colors.grey)),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(value, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
          const SizedBox(width: 10),
          const Icon(Icons.edit, size: 16, color: Colors.grey),
        ],
      ),
      onTap: onEdit,
    );
  }

  Widget _buildListTile(IconData icon, String title, VoidCallback onTap, {Color? textColor}) {
    return ListTile(
      onTap: onTap,
      leading: Icon(icon, color: textColor ?? Colors.deepPurple),
      title: Text(title, style: TextStyle(color: textColor, fontWeight: FontWeight.w500)),
      trailing: const Icon(Icons.arrow_forward_ios, size: 16),
    );
  }

  void _editGender() {
    String selected = _profile['gender'] ?? 'Male';
    showModalBottomSheet(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setModalState) => Container(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text("Edit Gender", style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
              const SizedBox(height: 20),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  _genderChoice("Male", selected, (val) => setModalState(() => selected = val)),
                  const SizedBox(width: 20),
                  _genderChoice("Female", selected, (val) => setModalState(() => selected = val)),
                ],
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: () async {
                  await UserManager.saveProfile(
                    gender: selected,
                    age: _profile['age'],
                    height: _profile['height'],
                    weight: _profile['weight'],
                  );
                  Navigator.pop(context);
                  _loadData();
                },
                child: const Text("Save"),
              )
            ],
          ),
        ),
      ),
    );
  }

  Widget _genderChoice(String val, String current, ValueChanged<String> onSelect) {
    bool isSel = val == current;
    return GestureDetector(
      onTap: () => onSelect(val),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
        decoration: BoxDecoration(color: isSel ? Colors.deepPurple : Colors.grey[200], borderRadius: BorderRadius.circular(10)),
        child: Text(val, style: TextStyle(color: isSel ? Colors.white : Colors.black)),
      ),
    );
  }

  void _editAge() {
    int val = _profile['age'] ?? 25;
    _showWheelDialog("Edit Age", WheelPicker(
      label: "YEARS", minValue: 10, maxValue: 100, initialValue: val,
      onChanged: (v) => val = v,
    ), () async {
      await UserManager.saveProfile(gender: _profile['gender'], age: val, height: _profile['height'], weight: _profile['weight']);
      _loadData();
    });
  }

  void _editHeight() {
    double current = _profile['height'] ?? 170.0;
    int hInt = current.floor();
    int hDec = ((current - hInt) * 10).round();
    _showWheelDialog("Edit Height", Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        WheelPicker(label: "CM", minValue: 100, maxValue: 250, initialValue: hInt, onChanged: (v) => hInt = v),
        const Text(".", style: TextStyle(fontSize: 30, fontWeight: FontWeight.bold)),
        WheelPicker(label: "", minValue: 0, maxValue: 9, initialValue: hDec, onChanged: (v) => hDec = v),
      ],
    ), () async {
      await UserManager.saveProfile(gender: _profile['gender'], age: _profile['age'], height: hInt + (hDec/10.0), weight: _profile['weight']);
      _loadData();
    });
  }

  void _editWeight() {
    double current = _profile['weight'] ?? 70.0;
    int wInt = current.floor();
    int wDec = ((current - wInt) * 10).round();
    _showWheelDialog("Edit Weight", Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        WheelPicker(label: "KG", minValue: 30, maxValue: 250, initialValue: wInt, onChanged: (v) => wInt = v),
        const Text(".", style: TextStyle(fontSize: 30, fontWeight: FontWeight.bold)),
        WheelPicker(label: "", minValue: 0, maxValue: 9, initialValue: wDec, onChanged: (v) => wDec = v),
      ],
    ), () async {
      await UserManager.updateWeightOnly(wInt + (wDec/10.0));
      _loadData();
    });
  }

  void _showWheelDialog(String title, Widget content, VoidCallback onSave) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: content,
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text("Cancel")),
          TextButton(onPressed: () { onSave(); Navigator.pop(context); }, child: const Text("Save")),
        ],
      ),
    );
  }

  void _showLogoutDialog(BuildContext context, AuthProvider authProvider) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text("Logout"),
        content: const Text("Are you sure you want to logout?"),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text("Cancel")),
          TextButton(onPressed: () {
            authProvider.logout();
            Navigator.of(context).pushAndRemoveUntil(MaterialPageRoute(builder: (context) => const AuthScreen()), (route) => false);
          }, child: const Text("Logout", style: TextStyle(color: Colors.red))),
        ],
      ),
    );
  }
}
