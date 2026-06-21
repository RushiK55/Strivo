import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../widgets/wheel_picker.dart';
import '../services/user_manager.dart';
import '../providers/AuthProvider.dart';
import 'HomeScreen.dart';

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
      backgroundColor: const Color(0xFFF8F9FE),
      body: SafeArea(
        child: Column(
          children: [
            const SizedBox(height: 30),
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
        return Container(
          margin: const EdgeInsets.symmetric(horizontal: 4),
          height: 8,
          width: _currentIndex == index ? 24 : 8,
          decoration: BoxDecoration(
            color: _currentIndex == index ? Colors.deepPurple : Colors.grey[300],
            borderRadius: BorderRadius.circular(4),
          ),
        );
      }),
    );
  }

  Widget _buildStepContainer({required String title, required String subtitle, required Widget child}) {
    return Padding(
      padding: const EdgeInsets.all(24.0),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(title, style: const TextStyle(fontSize: 28, fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          Text(subtitle, style: TextStyle(fontSize: 16, color: Colors.grey[600])),
          const SizedBox(height: 40),
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
          _genderButton("Male", Icons.male),
          const SizedBox(width: 20),
          _genderButton("Female", Icons.female),
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
        padding: const EdgeInsets.symmetric(horizontal: 30, vertical: 20),
        decoration: BoxDecoration(
          color: isSelected ? Colors.deepPurple : Colors.white,
          borderRadius: BorderRadius.circular(20),
          boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 10)],
        ),
        child: Column(
          children: [
            Icon(icon, size: 40, color: isSelected ? Colors.white : Colors.deepPurple),
            const SizedBox(height: 8),
            Text(value, style: TextStyle(color: isSelected ? Colors.white : Colors.black, fontWeight: FontWeight.bold)),
          ],
        ),
      ),
    );
  }

  Widget _buildAgeStep() {
    return _buildStepContainer(
      title: "How old are you?",
      subtitle: "Your age helps us calculate calories",
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
      subtitle: "In centimeters",
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
            child: Text(".", style: TextStyle(fontSize: 30, fontWeight: FontWeight.bold)),
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
      title: "Current weight?",
      subtitle: "In kilograms",
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
            child: Text(".", style: TextStyle(fontSize: 30, fontWeight: FontWeight.bold)),
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
      padding: const EdgeInsets.all(24.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          if (_currentIndex > 0)
            TextButton(
              onPressed: () => _pageController.previousPage(duration: const Duration(milliseconds: 500), curve: Curves.easeInOut),
              child: const Text("BACK", style: TextStyle(color: Colors.grey, fontWeight: FontWeight.bold)),
            )
          else
            const SizedBox(),
          ElevatedButton(
            onPressed: _nextPage,
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.deepPurple,
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 40, vertical: 15),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(15)),
            ),
            child: Text(_currentIndex == 3 ? "FINISH" : "NEXT", style: const TextStyle(fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }
}
