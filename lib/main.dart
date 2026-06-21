import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:strivo/Screens/HomeScreen.dart';
import 'package:strivo/Screens/AuthScreen.dart';
import 'package:strivo/Screens/OnboardingScreen.dart';
import 'package:strivo/providers/ExerciseProvider.dart';
import 'package:strivo/providers/PlanProvider.dart';
import 'package:strivo/providers/AuthProvider.dart';

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
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.deepPurple,
          primary: Colors.deepPurple,
        ),
        appBarTheme: const AppBarTheme(
          centerTitle: true,
          backgroundColor: Colors.white,
          foregroundColor: Colors.black,
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


