import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../widgets/wheel_picker.dart';
import '../services/user_manager.dart';
import '../providers/AuthProvider.dart';
import 'HomeScreen.dart';
import 'package:strivo/utils/app_colors.dart';

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final PageController _pageController = PageController();
  int _currentIndex = 0;

  String _gender = 'Male';
  int _age = 25;
  int _heightInt = 170;
  int _heightDecimal = 0;
  int _weightInt = 70;
  int _weightDecimal = 0;

  void _nextPage() {
    if (_currentIndex < 3) {
      _pageController.nextPage(
        duration: const Duration(milliseconds: 500),
        curve: Curves.easeInOut,
      );
    } else {
      _saveAndFinish();
    }
  }

  Future<void> _saveAndFinish() async {
    double height = _heightInt + (_heightDecimal / 10.0);
    double weight = _weightInt + (_weightDecimal / 10.0);

    await UserManager.saveProfile(
      gender: _gender,
      age: _age,
      height: height,
      weight: weight,
    );

    if (mounted) {
      await Provider.of<AuthProvider>(context, listen: false).refreshProfileStatus();
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(builder: (context) => const Homescreen()),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: Column(
          children: [
            const SizedBox(height: 40),
            _buildProgressIndicator(),
            Expanded(
              child: PageView(
                controller: _pageController,
                physics: const NeverScrollableScrollPhysics(),
                onPageChanged: (index) => setState(() => _currentIndex = index),
                children: [
                  _buildGenderStep(),
                  _buildAgeStep(),
                  _buildHeightStep(),
                  _buildWeightStep(),
                ],
              ),
            ),
            _buildBottomBar(),
          ],
        ),
      ),
    );
  }

  Widget _buildProgressIndicator() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: List.generate(4, (index) {
        bool isActive = _currentIndex == index;
        return AnimatedContainer(
          duration: const Duration(milliseconds: 300),
          margin: const EdgeInsets.symmetric(horizontal: 5),
          height: 8,
          width: isActive ? 30 : 8,
          decoration: BoxDecoration(
            color: isActive ? AppColors.accent : const Color(0xFF2C2C2E),
            borderRadius: BorderRadius.circular(10),
          ),
        );
      }),
    );
  }

  Widget _buildStepContainer({required String title, required String subtitle, required Widget child}) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32.0),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(
            title,
            textAlign: TextAlign.center,
            style: const TextStyle(
                fontSize: 32,
                fontWeight: FontWeight.w900,
                color: AppColors.textPrimary),
          ),
          const SizedBox(height: 12),
          Text(
            subtitle,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 16, color: AppColors.textSecondary),
          ),
          const SizedBox(height: 60),
          child,
        ],
      ),
    );
  }

  Widget _buildGenderStep() {
    return _buildStepContainer(
      title: "What's your gender?",
      subtitle: "Help us customize your experience",
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          _genderButton("Male", Icons.male_rounded),
          const SizedBox(width: 24),
          _genderButton("Female", Icons.female_rounded),
        ],
      ),
    );
  }

  Widget _genderButton(String value, IconData icon) {
    bool isSelected = _gender == value;
    return GestureDetector(
      onTap: () => setState(() => _gender = value),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 300),
        width: 120,
        height: 140,
        decoration: BoxDecoration(
          color: isSelected ? AppColors.accent : AppColors.surface,
          borderRadius: BorderRadius.circular(30),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 48, color: isSelected ? Colors.black : AppColors.accent),
            const SizedBox(height: 12),
            Text(
              value,
              style: TextStyle(
                  color: isSelected ? Colors.black : AppColors.textPrimary,
                  fontWeight: FontWeight.w900,
                  fontSize: 16),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAgeStep() {
    return _buildStepContainer(
      title: "How old are you?",
      subtitle: "Your age helps us calculate metrics",
      child: WheelPicker(
        label: "YEARS",
        minValue: 10,
        maxValue: 100,
        initialValue: _age,
        onChanged: (val) => setState(() => _age = val),
      ),
    );
  }

  Widget _buildHeightStep() {
    return _buildStepContainer(
      title: "What's your height?",
      subtitle: "Measure in centimeters",
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          WheelPicker(
            label: "CM",
            minValue: 100,
            maxValue: 250,
            initialValue: _heightInt,
            onChanged: (val) => setState(() => _heightInt = val),
          ),
          const Padding(
            padding: EdgeInsets.only(top: 40),
            child: Text(".",
                style: TextStyle(
                    fontSize: 40,
                    fontWeight: FontWeight.w900,
                    color: AppColors.textPrimary)),
          ),
          WheelPicker(
            label: "",
            minValue: 0,
            maxValue: 9,
            initialValue: _heightDecimal,
            onChanged: (val) => setState(() => _heightDecimal = val),
          ),
        ],
      ),
    );
  }

  Widget _buildWeightStep() {
    return _buildStepContainer(
      title: "What's your weight?",
      subtitle: "Measure in kilograms",
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          WheelPicker(
            label: "KG",
            minValue: 30,
            maxValue: 250,
            initialValue: _weightInt,
            onChanged: (val) => setState(() => _weightInt = val),
          ),
          const Padding(
            padding: EdgeInsets.only(top: 40),
            child: Text(".",
                style: TextStyle(
                    fontSize: 40,
                    fontWeight: FontWeight.w900,
                    color: AppColors.textPrimary)),
          ),
          WheelPicker(
            label: "",
            minValue: 0,
            maxValue: 9,
            initialValue: _weightDecimal,
            onChanged: (val) => setState(() => _weightDecimal = val),
          ),
        ],
      ),
    );
  }

  Widget _buildBottomBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(32, 24, 32, 40),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          if (_currentIndex > 0)
            TextButton(
              onPressed: () => _pageController.previousPage(
                  duration: const Duration(milliseconds: 500),
                  curve: Curves.easeInOut),
              child: const Text("BACK",
                  style: TextStyle(
                      color: AppColors.textSecondary,
                      fontWeight: FontWeight.w900,
                      letterSpacing: 1.2)),
            )
          else
            const SizedBox(),
          SizedBox(
            height: 55,
            width: 150,
            child: ElevatedButton(
              onPressed: _nextPage,
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.accent,
                foregroundColor: Colors.black,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                elevation: 0,
              ),
              child: Text(_currentIndex == 3 ? "FINISH" : "NEXT",
                  style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 16)),
            ),
          ),
        ],
      ),
    );
  }
}
