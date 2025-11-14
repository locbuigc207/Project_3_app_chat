import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart' as firebase_auth;
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_chat_demo/constants/constants.dart';
import 'package:flutter_chat_demo/pages/pages.dart';
import 'package:flutter_chat_demo/providers/providers.dart' hide PhoneAuthProvider;
import 'package:flutter_chat_demo/providers/phone_auth_provider.dart' as custom_auth;
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:timezone/data/latest.dart' as tz;


Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp();

  // Initialize timezone for scheduled notifications
  tz.initializeTimeZones();

  final prefs = await SharedPreferences.getInstance();

  // Initialize notifications
  final flutterLocalNotificationsPlugin = FlutterLocalNotificationsPlugin();
  const initializationSettingsAndroid = AndroidInitializationSettings('app_icon');
  const initializationSettingsIOS = DarwinInitializationSettings();
  const initializationSettings = InitializationSettings(
    android: initializationSettingsAndroid,
    iOS: initializationSettingsIOS,
  );
  await flutterLocalNotificationsPlugin.initialize(initializationSettings);

  runApp(MyApp(
    prefs: prefs,
    notificationsPlugin: flutterLocalNotificationsPlugin,
  ));
}

class MyApp extends StatelessWidget {
  final SharedPreferences prefs;
  final FlutterLocalNotificationsPlugin notificationsPlugin;

  const MyApp({
    required this.prefs,
    required this.notificationsPlugin,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final firebaseFirestore = FirebaseFirestore.instance;
    final firebaseStorage = FirebaseStorage.instance;
    final firebaseAuth = firebase_auth.FirebaseAuth.instance;

    return MultiProvider(
      providers: [
        /// Auth Provider (Google)
        ChangeNotifierProvider<AuthProvider>(
          create: (_) => AuthProvider(
            firebaseAuth: firebaseAuth,
            googleSignIn: GoogleSignIn(),
            prefs: prefs,
            firebaseFirestore: firebaseFirestore,
          ),
        ),

        /// Phone Auth Provider (Custom)
        ChangeNotifierProvider<custom_auth.PhoneAuthProvider>(
          create: (_) => custom_auth.PhoneAuthProvider(
            firebaseAuth: firebaseAuth,
            firebaseFirestore: firebaseFirestore,
            prefs: prefs,
          ),
        ),

        /// Settings Provider
        Provider<SettingProvider>(
          create: (_) => SettingProvider(
            prefs: prefs,
            firebaseFirestore: firebaseFirestore,
            firebaseStorage: firebaseStorage,
          ),
        ),

        /// Home Provider
        Provider<HomeProvider>(
          create: (_) => HomeProvider(firebaseFirestore: firebaseFirestore),
        ),

        /// Chat Provider
        Provider<ChatProvider>(
          create: (_) => ChatProvider(
            prefs: prefs,
            firebaseFirestore: firebaseFirestore,
            firebaseStorage: firebaseStorage,
          ),
        ),

        /// Friend Provider
        Provider<FriendProvider>(
          create: (_) => FriendProvider(firebaseFirestore: firebaseFirestore),
        ),

        /// Reaction Provider
        Provider<ReactionProvider>(
          create: (_) => ReactionProvider(firebaseFirestore: firebaseFirestore),
        ),

        /// Message Provider
        Provider<MessageProvider>(
          create: (_) => MessageProvider(firebaseFirestore: firebaseFirestore),
        ),

        /// Conversation Provider
        Provider<ConversationProvider>(
          create: (_) =>
              ConversationProvider(firebaseFirestore: firebaseFirestore),
        ),

        /// Theme Provider
        ChangeNotifierProvider<ThemeProvider>(
          create: (_) => ThemeProvider(prefs: prefs),
        ),

        /// NEW PROVIDERS ///

        /// Reminder Provider
        Provider<ReminderProvider>(
          create: (_) => ReminderProvider(
            firebaseFirestore: firebaseFirestore,
            notificationsPlugin: notificationsPlugin,
          ),
        ),

        /// Auto Delete Provider
        Provider<AutoDeleteProvider>(
          create: (_) => AutoDeleteProvider(
            firebaseFirestore: firebaseFirestore,
          ),
        ),

        /// Conversation Lock Provider
        Provider<ConversationLockProvider>(
          create: (_) => ConversationLockProvider(
            firebaseFirestore: firebaseFirestore,
            prefs: prefs,
          ),
        ),

        /// View Once Provider
        Provider<ViewOnceProvider>(
          create: (_) => ViewOnceProvider(
            firebaseFirestore: firebaseFirestore,
          ),
        ),

        /// Smart Reply Provider
        Provider<SmartReplyProvider>(
          create: (_) => SmartReplyProvider(),
        ),
      ],
      child: Consumer<ThemeProvider>(
        builder: (context, themeProvider, child) {
          return MaterialApp(
            title: AppConstants.appTitle,
            debugShowCheckedModeBanner: false,
            themeMode: themeProvider.getFlutterThemeMode(context),
            theme: AppThemes.lightTheme(themeProvider.getPrimaryColor()),
            darkTheme: AppThemes.darkTheme(themeProvider.getPrimaryColor()),
            home: SplashPage(),
          );
        },
      ),
    );
  }
}