import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/AuthProvider.dart';
import '../services/user_manager.dart';
import '../widgets/wheel_picker.dart';
import '../database/database_helper.dart';
import 'AuthScreen.dart';
import 'HistoryScreen.dart';
import 'package:strivo/utils/app_colors.dart';

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

    if (_isLoading) {
      return const Scaffold(
          backgroundColor: AppColors.background,
          body: Center(child: CircularProgressIndicator(color: AppColors.accent)));
    }

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text("Profile",
            style: TextStyle(fontWeight: FontWeight.w900, color: AppColors.textPrimary, letterSpacing: 1.0)),
        elevation: 0,
        backgroundColor: Colors.transparent,
        centerTitle: true,
        leading: IconButton(
          icon: Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: AppColors.surface,
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.arrow_back_ios_new, size: 16, color: AppColors.textPrimary),
          ),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      extendBodyBehindAppBar: true,
      body: SingleChildScrollView(
        physics: const BouncingScrollPhysics(),
        child: Column(
          children: [
            Container(
              width: double.infinity,
              padding: const EdgeInsets.only(top: 140, bottom: 40),
              decoration: const BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.only(
                  bottomLeft: Radius.circular(50),
                  bottomRight: Radius.circular(50),
                ),
              ),
              child: Column(
                children: [
                  Stack(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(4),
                        decoration: const BoxDecoration(
                          color: AppColors.accent,
                          shape: BoxShape.circle,
                        ),
                        child: const CircleAvatar(
                          radius: 60,
                          backgroundColor: Color(0xFF2C2C2E),
                          child: Icon(Icons.person_rounded, size: 70, color: AppColors.textPrimary),
                        ),
                      ),
                      Positioned(
                        bottom: 5,
                        right: 5,
                        child: Container(
                          padding: const EdgeInsets.all(6),
                          decoration: const BoxDecoration(
                            color: AppColors.accent,
                            shape: BoxShape.circle,
                          ),
                          child: const Icon(Icons.edit_rounded, size: 16, color: Colors.black),
                        ),
                      )
                    ],
                  ),
                  const SizedBox(height: 24),
                  Text(
                    authProvider.userName ?? "User",
                    style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontSize: 28,
                        fontWeight: FontWeight.w900,
                        letterSpacing: 1.0),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    authProvider.userEmail ?? "",
                    style: const TextStyle(color: AppColors.textSecondary, fontSize: 16, fontWeight: FontWeight.w500),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildSectionTitle("Weight Journey"),
                  _buildWeightStats(),
                  const SizedBox(height: 40),
                  _buildSectionTitle("Peak Performance"),
                  _buildPersonalRecords(),
                  const SizedBox(height: 40),
                  _buildSectionTitle("Personal Details"),
                  _buildProfileDetails(),
                  const SizedBox(height: 40),
                  _buildSectionTitle("More"),
                  Container(
                    decoration: BoxDecoration(
                      color: AppColors.surface,
                      borderRadius: BorderRadius.circular(30),
                      border: Border.all(color: const Color(0xFF2C2C2E)),
                    ),
                    child: Column(
                      children: [
                        _buildListTile(Icons.history_rounded, "View Workout History", () {
                          Navigator.push(
                              context,
                              MaterialPageRoute(
                                  builder: (context) => const HistoryScreen()));
                        }),
                        const Divider(color: Color(0xFF2C2C2E), height: 1, indent: 60),
                        _buildListTile(Icons.logout_rounded, "Logout Account",
                            () => _showLogoutDialog(context, authProvider),
                            textColor: Colors.redAccent),
                      ],
                    ),
                  ),
                  const SizedBox(height: 60),
                ],
              ),
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
              fontSize: 13,
              fontWeight: FontWeight.bold,
              color: AppColors.textPrimary,
              letterSpacing: 1.2),
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

  Widget _buildPersonalRecords() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(25),
      ),
      child: Column(
        children: [
          _recordRow(
              "Highest Lift",
              "${_records['maxWeight']?.toStringAsFixed(1) ?? '0.0'} kg",
              Icons.fitness_center_rounded),
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 12),
            child: Divider(color: Color(0xFF2C2C2E)),
          ),
          _recordRow("Most Reps", "${_records['maxReps']?.toInt() ?? '0'} reps",
              Icons.repeat_rounded),
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 12),
            child: Divider(color: Color(0xFF2C2C2E)),
          ),
          _recordRow(
              "Best Volume",
              "${_records['maxVolume']?.toStringAsFixed(1) ?? '0.0'} kg",
              Icons.equalizer_rounded),
        ],
      ),
    );
  }

  Widget _recordRow(String label, String value, IconData icon) {
    return Row(
      children: [
        Container(
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: const Color(0xFF2C2C2E),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Icon(icon, color: AppColors.accent, size: 20),
        ),
        const SizedBox(width: 15),
        Text(label,
            style: const TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w500,
                color: AppColors.textPrimary)),
        const Spacer(),
        Text(value,
            style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: AppColors.accent)),
      ],
    );
  }

  Widget _buildWeightStats() {
    return Row(
      children: [
        _statCard(
            "HIGHEST",
            "${_profile['highWeight']?.toStringAsFixed(1) ?? '--'} kg",
            Colors.redAccent.withOpacity(0.1),
            Colors.redAccent),
        const SizedBox(width: 15),
        _statCard(
            "LOWEST",
            "${_profile['lowWeight']?.toStringAsFixed(1) ?? '--'} kg",
            Colors.greenAccent.withOpacity(0.1),
            Colors.greenAccent),
      ],
    );
  }

  Widget _statCard(String label, String value, Color bg, Color text) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(20)),
        child: Column(
          children: [
            Text(label,
                style: TextStyle(
                    fontSize: 10, fontWeight: FontWeight.bold, color: text, letterSpacing: 1.0)),
            const SizedBox(height: 8),
            Text(value,
                style: TextStyle(
                    fontSize: 22, fontWeight: FontWeight.w900, color: text)),
          ],
        ),
      ),
    );
  }

  Widget _buildProfileDetails() {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(25),
      ),
      child: Column(
        children: [
          _buildDetailRow("Gender", _profile['gender'] ?? "--", Icons.wc_rounded,
              () => _editGender()),
          const Divider(color: Color(0xFF2C2C2E), height: 1, indent: 60),
          _buildDetailRow("Age", "${_profile['age'] ?? '--'} yrs",
              Icons.calendar_today_rounded, () => _editAge()),
          const Divider(color: Color(0xFF2C2C2E), height: 1, indent: 60),
          _buildDetailRow(
              "Height",
              "${_profile['height']?.toStringAsFixed(1) ?? '--'} cm",
              Icons.height_rounded,
              () => _editHeight()),
          const Divider(color: Color(0xFF2C2C2E), height: 1, indent: 60),
          _buildDetailRow(
              "Weight",
              "${_profile['weight']?.toStringAsFixed(1) ?? '--'} kg",
              Icons.monitor_weight_rounded,
              () => _editWeight()),
        ],
      ),
    );
  }

  Widget _buildDetailRow(
      String label, String value, IconData icon, VoidCallback onEdit) {
    return ListTile(
      leading: Icon(icon, color: AppColors.textSecondary, size: 22),
      title: Text(label,
          style: const TextStyle(fontSize: 14, color: AppColors.textSecondary)),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(value,
              style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                  color: AppColors.textPrimary)),
          const SizedBox(width: 10),
          const Icon(Icons.arrow_forward_ios, size: 14, color: AppColors.textSecondary),
        ],
      ),
      onTap: onEdit,
    );
  }

  Widget _buildListTile(IconData icon, String title, VoidCallback onTap,
      {Color? textColor}) {
    return ListTile(
      onTap: onTap,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      leading: Icon(icon, color: textColor ?? AppColors.accent),
      title: Text(title,
          style: TextStyle(
              color: textColor ?? AppColors.textPrimary, fontWeight: FontWeight.w500)),
      trailing: const Icon(Icons.arrow_forward_ios, size: 16, color: AppColors.textSecondary),
    );
  }

  void _editGender() {
    String selected = _profile['gender'] ?? 'Male';
    showModalBottomSheet(
      context: context,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(30))),
      builder: (context) => StatefulBuilder(
        builder: (context, setModalState) => Container(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text("Edit Gender",
                  style: TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                      color: AppColors.textPrimary)),
              const SizedBox(height: 30),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  _genderChoice("Male", selected,
                      (val) => setModalState(() => selected = val)),
                  const SizedBox(width: 20),
                  _genderChoice("Female", selected,
                      (val) => setModalState(() => selected = val)),
                ],
              ),
              const SizedBox(height: 40),
              SizedBox(
                width: double.infinity,
                height: 55,
                child: ElevatedButton(
                  onPressed: () async {
                    await UserManager.saveProfile(
                      gender: selected,
                      age: _profile['age'],
                      height: _profile['height'],
                      weight: _profile['weight'],
                    );
                    if (context.mounted) Navigator.pop(context);
                    _loadData();
                  },
                  style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.accent,
                      foregroundColor: Colors.black,
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(15))),
                  child: const Text("SAVE CHANGES",
                      style: TextStyle(fontWeight: FontWeight.bold)),
                ),
              )
            ],
          ),
        ),
      ),
    );
  }

  Widget _genderChoice(
      String val, String current, ValueChanged<String> onSelect) {
    bool isSel = val == current;
    return GestureDetector(
      onTap: () => onSelect(val),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 30, vertical: 15),
        decoration: BoxDecoration(
            color: isSel ? AppColors.accent : const Color(0xFF2C2C2E),
            borderRadius: BorderRadius.circular(15)),
        child: Text(val,
            style: TextStyle(
                color: isSel ? Colors.black : AppColors.textPrimary,
                fontWeight: FontWeight.bold)),
      ),
    );
  }

  void _editAge() {
    int val = _profile['age'] ?? 25;
    _showWheelDialog(
        "Edit Age",
        WheelPicker(
          label: "YEARS",
          minValue: 10,
          maxValue: 100,
          initialValue: val,
          onChanged: (v) => val = v,
        ), () async {
      await UserManager.saveProfile(
          gender: _profile['gender'],
          age: val,
          height: _profile['height'],
          weight: _profile['weight']);
      _loadData();
    });
  }

  void _editHeight() {
    double current = _profile['height'] ?? 170.0;
    int hInt = current.floor();
    int hDec = ((current - hInt) * 10).round();
    _showWheelDialog(
        "Edit Height",
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            WheelPicker(
                label: "CM",
                minValue: 100,
                maxValue: 250,
                initialValue: hInt,
                onChanged: (v) => hInt = v),
            const Text(".",
                style: TextStyle(
                    fontSize: 30,
                    fontWeight: FontWeight.bold,
                    color: AppColors.textPrimary)),
            WheelPicker(
                label: "",
                minValue: 0,
                maxValue: 9,
                initialValue: hDec,
                onChanged: (v) => hDec = v),
          ],
        ), () async {
      await UserManager.saveProfile(
          gender: _profile['gender'],
          age: _profile['age'],
          height: hInt + (hDec / 10.0),
          weight: _profile['weight']);
      _loadData();
    });
  }

  void _editWeight() {
    double current = _profile['weight'] ?? 70.0;
    int wInt = current.floor();
    int wDec = ((current - wInt) * 10).round();
    _showWheelDialog(
        "Edit Weight",
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            WheelPicker(
                label: "KG",
                minValue: 30,
                maxValue: 250,
                initialValue: wInt,
                onChanged: (v) => wInt = v),
            const Text(".",
                style: TextStyle(
                    fontSize: 30,
                    fontWeight: FontWeight.bold,
                    color: AppColors.textPrimary)),
            WheelPicker(
                label: "",
                minValue: 0,
                maxValue: 9,
                initialValue: wDec,
                onChanged: (v) => wDec = v),
          ],
        ), () async {
      await UserManager.updateWeightOnly(wInt + (wDec / 10.0));
      _loadData();
    });
  }

  void _showWheelDialog(String title, Widget content, VoidCallback onSave) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.surface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(25)),
        title: Text(title, style: const TextStyle(color: AppColors.textPrimary)),
        content: content,
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text("Cancel", style: TextStyle(color: Colors.redAccent))),
          TextButton(
              onPressed: () {
                onSave();
                Navigator.pop(context);
              },
              child: const Text("Save",
                  style: TextStyle(
                      color: AppColors.accent, fontWeight: FontWeight.bold))),
        ],
      ),
    );
  }

  void _showLogoutDialog(BuildContext context, AuthProvider authProvider) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.surface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(25)),
        title: const Text("Logout", style: TextStyle(color: AppColors.textPrimary)),
        content: const Text("Are you sure you want to logout?",
            style: TextStyle(color: AppColors.textSecondary)),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text("Cancel",
                  style: TextStyle(color: AppColors.textSecondary))),
          TextButton(
              onPressed: () {
                authProvider.logout();
                Navigator.of(context).pushAndRemoveUntil(
                    MaterialPageRoute(builder: (context) => const AuthScreen()),
                    (route) => false);
              },
              child: const Text("Logout", style: TextStyle(color: Colors.redAccent))),
        ],
      ),
    );
  }
}
