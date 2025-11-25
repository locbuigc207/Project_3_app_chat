import 'dart:io';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart' as firebase_auth;
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_chat_demo/constants/constants.dart';
import 'package:flutter_chat_demo/pages/pages.dart';
import 'package:flutter_chat_demo/providers/phone_auth_provider.dart'
    as custom_auth;
import 'package:flutter_chat_demo/providers/providers.dart';
import 'package:flutter_chat_demo/services/services.dart';
import 'package:flutter_chat_demo/utils/utils.dart';
import 'package:flutter_chat_demo/widgets/widgets.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:timezone/data/latest_all.dart' as tz;
import 'package:timezone/timezone.dart' as tz;

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  try {
    await Firebase.initializeApp();
    print('✅ Firebase initialized successfully');
  } catch (e) {
    print('❌ Firebase initialization error: $e');
  }

  await ErrorLogger.initialize();

  tz.initializeTimeZones();
  tz.setLocalLocation(tz.getLocation('Asia/Ho_Chi_Minh'));

  final prefs = await SharedPreferences.getInstance();

  final flutterLocalNotificationsPlugin = FlutterLocalNotificationsPlugin();
  await _initializeNotifications(flutterLocalNotificationsPlugin);

  // ✅ NEW: Initialize Chat Bubble Service
  final chatBubbleService = ChatBubbleService();
  final notificationService = NotificationService();

  print('✅ App initialized successfully');

  runApp(MyApp(
    prefs: prefs,
    notificationsPlugin: flutterLocalNotificationsPlugin,
    chatBubbleService: chatBubbleService,
    notificationService: notificationService,
  ));
}

Future<void> _initializeNotifications(
  FlutterLocalNotificationsPlugin plugin,
) async {
  const initializationSettingsAndroid =
      AndroidInitializationSettings('app_icon');

  const initializationSettingsIOS = DarwinInitializationSettings(
    requestAlertPermission: true,
    requestBadgePermission: true,
    requestSoundPermission: true,
  );

  const initializationSettings = InitializationSettings(
    android: initializationSettingsAndroid,
    iOS: initializationSettingsIOS,
  );

  await plugin.initialize(
    initializationSettings,
    onDidReceiveNotificationResponse: (NotificationResponse response) {
      print('📱 Notification clicked: ${response.payload}');
    },
  );

  // Request permissions
  if (Platform.isAndroid) {
    final androidPlugin = plugin.resolvePlatformSpecificImplementation<
        AndroidFlutterLocalNotificationsPlugin>();

    await androidPlugin?.requestNotificationsPermission();
    await androidPlugin?.requestExactAlarmsPermission();

    // Create notification channel
    await androidPlugin?.createNotificationChannel(
      const AndroidNotificationChannel(
        'message_reminders',
        'Message Reminders',
        description: 'Reminders for messages',
        importance: Importance.high,
      ),
    );
  }

  if (Platform.isIOS) {
    await plugin
        .resolvePlatformSpecificImplementation<
            IOSFlutterLocalNotificationsPlugin>()
        ?.requestPermissions(
          alert: true,
          badge: true,
          sound: true,
        );
  }
}

class MyApp extends StatelessWidget {
  final SharedPreferences prefs;
  final FlutterLocalNotificationsPlugin notificationsPlugin;
  final ChatBubbleService chatBubbleService;
  final NotificationService notificationService;

  const MyApp({
    super.key,
    required this.prefs,
    required this.notificationsPlugin,
    required this.chatBubbleService,
    required this.notificationService,
  });

  @override
  Widget build(BuildContext context) {
    final firebaseFirestore = FirebaseFirestore.instance;
    final firebaseStorage = FirebaseStorage.instance;
    final firebaseAuth = firebase_auth.FirebaseAuth.instance;

    final googleSignIn = GoogleSignIn(
      scopes: ['email', 'profile'],
    );

    return MultiProvider(
      providers: [
        // Auth Providers
        ChangeNotifierProvider<AuthProvider>(
          create: (_) => AuthProvider(
            firebaseAuth: firebaseAuth,
            googleSignIn: googleSignIn,
            prefs: prefs,
            firebaseFirestore: firebaseFirestore,
          ),
        ),
        ChangeNotifierProvider<custom_auth.PhoneAuthProvider>(
          create: (_) => custom_auth.PhoneAuthProvider(
            firebaseAuth: firebaseAuth,
            firebaseFirestore: firebaseFirestore,
            prefs: prefs,
          ),
        ),

        // Core Providers
        Provider<SettingProvider>(
          create: (_) => SettingProvider(
            prefs: prefs,
            firebaseFirestore: firebaseFirestore,
            firebaseStorage: firebaseStorage,
          ),
        ),
        Provider<HomeProvider>(
          create: (_) => HomeProvider(firebaseFirestore: firebaseFirestore),
        ),
        Provider<ChatProvider>(
          create: (_) => ChatProvider(
            prefs: prefs,
            firebaseFirestore: firebaseFirestore,
            firebaseStorage: firebaseStorage,
          ),
        ),

        // Feature Providers
        Provider<FriendProvider>(
          create: (_) => FriendProvider(firebaseFirestore: firebaseFirestore),
        ),
        Provider<ReactionProvider>(
          create: (_) => ReactionProvider(firebaseFirestore: firebaseFirestore),
        ),
        Provider<MessageProvider>(
          create: (_) => MessageProvider(firebaseFirestore: firebaseFirestore),
        ),
        Provider<ConversationProvider>(
          create: (_) =>
              ConversationProvider(firebaseFirestore: firebaseFirestore),
        ),

        // Theme
        ChangeNotifierProvider<ThemeProvider>(
          create: (_) => ThemeProvider(prefs: prefs),
        ),

        // Advanced Features
        Provider<ReminderProvider>(
          create: (_) => ReminderProvider(
            firebaseFirestore: firebaseFirestore,
            notificationsPlugin: notificationsPlugin,
          ),
        ),
        Provider<AutoDeleteProvider>(
          create: (_) =>
              AutoDeleteProvider(firebaseFirestore: firebaseFirestore),
        ),
        Provider<ConversationLockProvider>(
          create: (_) =>
              ConversationLockProvider(firebaseFirestore: firebaseFirestore),
        ),
        Provider<ViewOnceProvider>(
          create: (_) => ViewOnceProvider(firebaseFirestore: firebaseFirestore),
        ),
        Provider<SmartReplyProvider>(
          create: (_) => SmartReplyProvider(),
        ),
        Provider<UserPresenceProvider>(
          create: (_) =>
              UserPresenceProvider(firebaseFirestore: firebaseFirestore),
        ),

        // ✅ NEW: Chat Bubble Services
        Provider<ChatBubbleService>(
          create: (_) => chatBubbleService,
        ),
        Provider<NotificationService>(
          create: (_) => notificationService,
        ),

        // Location Provider (lazy initialization)
        Provider<LocationProvider>(
          create: (_) => LocationProvider(),
        ),

        Provider<TranslationProvider>(
          create: (_) => TranslationProvider(),
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
            home: BubbleManager(
              child: AppInitializer(
                notificationService: notificationService,
              ),
            ),
          );
        },
      ),
    );
  }
}

/// ✅ NEW: App initializer that starts notification service
class AppInitializer extends StatefulWidget {
  final NotificationService notificationService;

  const AppInitializer({
    super.key,
    required this.notificationService,
  });

  @override
  State<AppInitializer> createState() => _AppInitializerState();
}

class _AppInitializerState extends State<AppInitializer>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _startNotificationService();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);

    print('📱 App lifecycle: $state');

    if (state == AppLifecycleState.paused) {
      print('⏸️ App going to background');
    } else if (state == AppLifecycleState.resumed) {
      print('▶️ App resumed');
    }
  }

  /// ✅ Start notification service when user logs in
  Future<void> _startNotificationService() async {
    // Wait for auth to initialize
    await Future.delayed(Duration(milliseconds: 500));

    final auth = firebase_auth.FirebaseAuth.instance;
    auth.authStateChanges().listen((user) {
      if (user != null) {
        print('✅ User logged in, starting notification service');
        widget.notificationService.listenForNewMessages(user.uid);
      } else {
        print('❌ User logged out, stopping notification service');
        widget.notificationService.stopListening();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return SplashPage();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }
}
