import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:strivo/Screens/HomeScreen.dart';
import 'package:strivo/Screens/AuthScreen.dart';
import 'package:strivo/Screens/OnboardingScreen.dart';
import 'package:strivo/providers/ExerciseProvider.dart';
import 'package:strivo/providers/PlanProvider.dart';
import 'package:strivo/providers/AuthProvider.dart';
import 'package:strivo/utils/app_colors.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(
    MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_)=>Exerciseprovider()),
          ChangeNotifierProvider(create: (_)=>Planprovider()),
          ChangeNotifierProvider(create: (_)=>AuthProvider()),
        ], child: MyApp()),
  );
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Strivo',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        scaffoldBackgroundColor: AppColors.background,
        colorScheme: const ColorScheme.dark(
          primary: AppColors.accent,
          surface: AppColors.surface,
          onSurface: AppColors.textPrimary,
        ),
        appBarTheme: const AppBarTheme(
          centerTitle: true,
          backgroundColor: AppColors.background,
          foregroundColor: AppColors.textPrimary,
          elevation: 0,
        ),
      ),
      home: Consumer<AuthProvider>(
        builder: (context, auth, child) {
          if (auth.isAuthenticated) {
            if (auth.isProfileComplete) {
              return const Homescreen();
            } else {
              return const OnboardingScreen();
            }
          } else {
            return const AuthScreen();
          }
        },
      ),
    );
  }
}


