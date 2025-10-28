import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart' hide AuthProvider;
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_chat_demo/constants/app_constants.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'constants/color_constants.dart';
import 'pages/pages.dart';
import 'providers/providers.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp();
  final prefs = await SharedPreferences.getInstance();
  runApp(MyApp(prefs: prefs));
}

class MyApp extends StatelessWidget {
  final SharedPreferences prefs;

  MyApp({required this.prefs, super.key});

  final FirebaseFirestore _firebaseFirestore = FirebaseFirestore.instance;
  final FirebaseStorage _firebaseStorage = FirebaseStorage.instance;
  final FirebaseAuth _firebaseAuth = FirebaseAuth.instance;

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        /// Google Auth Provider
        ChangeNotifierProvider<AuthProvider>(
          create: (_) => AuthProvider(
            firebaseAuth: _firebaseAuth,
            googleSignIn: GoogleSignIn(),
            prefs: prefs,
            firebaseFirestore: _firebaseFirestore,
          ),
        ),

        /// NEW: Phone Auth Provider
        ChangeNotifierProvider<PhoneAuthProvider>(
          create: (_) => PhoneAuthProvider(
            firebaseAuth: _firebaseAuth,
            firebaseFirestore: _firebaseFirestore,
            prefs: prefs,
          ),
        ),

        /// Settings Provider
        Provider<SettingProvider>(
          create: (_) => SettingProvider(
            prefs: prefs,
            firebaseFirestore: _firebaseFirestore,
            firebaseStorage: _firebaseStorage,
          ),
        ),

        /// Home Provider
        Provider<HomeProvider>(
          create: (_) => HomeProvider(
            firebaseFirestore: _firebaseFirestore,
          ),
        ),

        /// Chat Provider
        Provider<ChatProvider>(
          create: (_) => ChatProvider(
            prefs: prefs,
            firebaseFirestore: _firebaseFirestore,
            firebaseStorage: _firebaseStorage,
          ),
        ),
      ],
      child: MaterialApp(
        title: AppConstants.appTitle,
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          useMaterial3: true,
          colorSchemeSeed: ColorConstants.themeColor,
        ),
        home: const SplashPage(),
      ),
    );
  }
}
