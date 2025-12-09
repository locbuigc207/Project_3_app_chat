import 'dart:io';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart' as firebase_auth;
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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

// ✅ FIX: Global notification plugin instance để khắc phục lỗi Null Context
final FlutterLocalNotificationsPlugin flutterLocalNotificationsPlugin =
    FlutterLocalNotificationsPlugin();

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  try {
    await Firebase.initializeApp();
    print('✅ Firebase initialized successfully');
  } catch (e) {
    print('❌ Firebase initialization error: $e');
  }

  // Khởi tạo các dịch vụ cần thiết
  await ErrorLogger.initialize();
  tz.initializeTimeZones();
  tz.setLocalLocation(tz.getLocation('Asia/Ho_Chi_Minh'));
  final prefs = await SharedPreferences.getInstance();

  // ✅ FIX: Initialize notifications VỚI INSTANCE TOÀN CỤC
  await _initializeNotifications(flutterLocalNotificationsPlugin);

  // Khởi tạo các Service
  final chatBubbleService = ChatBubbleService();
  final notificationService = NotificationService();

  print('✅ App initialized successfully');

  runApp(MyApp(
    prefs: prefs,
    // Truyền instance toàn cục
    notificationsPlugin: flutterLocalNotificationsPlugin,
    chatBubbleService: chatBubbleService,
    notificationService: notificationService,
  ));
}

Future<void> _initializeNotifications(
  FlutterLocalNotificationsPlugin plugin,
) async {
  try {
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

    // ✅ FIX: Initialize with error handling
    await plugin.initialize(
      initializationSettings,
      onDidReceiveNotificationResponse: (NotificationResponse response) {
        print('📱 Notification clicked: ${response.payload}');
      },
    );

    if (Platform.isAndroid) {
      final androidPlugin = plugin.resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin>();

      if (androidPlugin != null) {
        await androidPlugin.requestNotificationsPermission();
        await androidPlugin.requestExactAlarmsPermission();

        await androidPlugin.createNotificationChannel(
          const AndroidNotificationChannel(
            'message_reminders',
            'Message Reminders',
            description: 'Reminders for messages',
            importance: Importance.high,
          ),
        );
      }
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
    print('✅ Notifications initialized successfully');
  } catch (e) {
    print('❌ Notification initialization error: $e');
    // ✅ Don't crash the app, just log the error
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
        ChangeNotifierProvider<ThemeProvider>(
          create: (_) => ThemeProvider(prefs: prefs),
        ),
        // ✅ FIX: Sử dụng instance notificationsPlugin được truyền vào
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
        Provider<ChatBubbleService>(
          create: (_) => chatBubbleService,
        ),
        Provider<NotificationService>(
          create: (_) => notificationService,
        ),
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
            // ✅ Bọc MaterialApp bằng BubbleManager và MiniChatOverlayManager
            home: BubbleManager(
              child: MiniChatOverlayManager(
                child: AppInitializer(
                  notificationService: notificationService,
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

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

  Future<void> _startNotificationService() async {
    await Future.delayed(const Duration(milliseconds: 500));

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
    // Sẽ điều hướng đến MainPage sau khi SplashPage hoàn tất
    return SplashPage();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }
}

// ===========================================
// ✅ Mini Chat Overlay Implementation (Gộp và Cập nhật)
// ===========================================

/// Quản lý MethodChannel và OverlayEntry cho Mini Chat.
class MiniChatOverlayManager extends StatefulWidget {
  final Widget child;

  const MiniChatOverlayManager({super.key, required this.child});

  @override
  State<MiniChatOverlayManager> createState() => _MiniChatOverlayManagerState();
}

class _MiniChatOverlayManagerState extends State<MiniChatOverlayManager> {
  // Channel này nhận lệnh từ Native (MiniChatChannel trên Native)
  static const MethodChannel _miniChatChannel =
      MethodChannel('mini_chat_channel');

  OverlayEntry? _miniChatOverlay;
  String? _currentUserId;
  String? _currentUserName;
  String? _currentUserAvatar;

  @override
  void initState() {
    super.initState();
    _setupMiniChatChannel();
  }

  void _setupMiniChatChannel() {
    print('✅ Setting up MiniChat MethodChannel');

    _miniChatChannel.setMethodCallHandler((call) async {
      print('📥 MiniChat Channel received: ${call.method}');
      print('📥 Arguments: ${call.arguments}');

      if (call.method == 'navigateToMiniChat') {
        final peerId = call.arguments['peerId'] as String?;
        final peerNickname = call.arguments['peerNickname'] as String?;
        final peerAvatar = call.arguments['peerAvatar'] as String?;

        if (peerId == null || peerNickname == null) {
          print('❌ Missing required arguments for navigation');
          return null;
        }

        print('💬 Showing mini chat overlay for: $peerNickname');
        _showMiniChatOverlay(peerId, peerNickname, peerAvatar ?? '');
      } else if (call.method == 'minimize') {
        print('📦 Minimizing mini chat');
        _hideMiniChatOverlay();
        // Native sẽ tự hiển thị lại Bubble sau khi nhận lệnh minimize
      } else if (call.method == 'close') {
        print('❌ Closing mini chat');
        _hideMiniChatOverlay();
        // Native sẽ tự xóa Bubble sau khi nhận lệnh close
      }

      return null;
    });

    print('✅ MiniChat MethodChannel setup complete');
  }

  void _showMiniChatOverlay(String userId, String userName, String avatarUrl) {
    // Xóa overlay cũ nếu có
    _hideMiniChatOverlay();

    _currentUserId = userId;
    _currentUserName = userName;
    _currentUserAvatar = avatarUrl;

    // Tạo Overlay Entry (Sử dụng kích thước và kiểu dáng từ bản FIX)
    _miniChatOverlay = OverlayEntry(
      builder: (context) => MiniChatOverlayWidget(
        userId: userId,
        userName: userName,
        avatarUrl: avatarUrl,
        // Gọi lại Native khi Minimize
        onMinimize: () {
          print('📦 Minimize button pressed');
          _hideMiniChatOverlay();
          // Thông báo cho Native để hiển thị lại bubble
          _miniChatChannel.invokeMethod('onMinimized', {
            'userId': userId,
          });
        },
        // Gọi lại Native khi Close
        onClose: () {
          print('❌ Close button pressed');
          _hideMiniChatOverlay();
          // Thông báo cho Native để xóa bubble
          _miniChatChannel.invokeMethod('onClosed', {
            'userId': userId,
          });
        },
      ),
    );

    // Chèn vào Overlay
    Overlay.of(context).insert(_miniChatOverlay!);
    print('✅ Mini chat overlay shown');
  }

  void _hideMiniChatOverlay() {
    if (_miniChatOverlay != null) {
      _miniChatOverlay!.remove();
      _miniChatOverlay = null;
      _currentUserId = null;
      _currentUserName = null;
      _currentUserAvatar = null;
      print('✅ Mini chat overlay hidden');
    }
  }

  @override
  Widget build(BuildContext context) {
    // Trả về widget con (AppInitializer)
    return widget.child;
  }

  @override
  void dispose() {
    _hideMiniChatOverlay();
    super.dispose();
  }
}

/// Widget hiển thị Mini Chat (ChatPage) có thể kéo thả, nằm trong Overlay.
class MiniChatOverlayWidget extends StatefulWidget {
  final String userId;
  final String userName;
  final String avatarUrl;
  final VoidCallback onMinimize;
  final VoidCallback onClose;

  const MiniChatOverlayWidget({
    super.key,
    required this.userId,
    required this.userName,
    required this.avatarUrl,
    required this.onMinimize,
    required this.onClose,
  });

  @override
  State<MiniChatOverlayWidget> createState() => _MiniChatOverlayWidgetState();
}

class _MiniChatOverlayWidgetState extends State<MiniChatOverlayWidget> {
  // Vị trí ban đầu của cửa sổ mini chat
  Offset _position = const Offset(20, 100);
  bool _isDragging = false;
  // Sử dụng kích thước từ bản FIX thứ hai (340x500)
  static const double _width = 340;
  static const double _height = 500;

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.of(context).size;
    final maxX = size.width - _width;
    final maxY = size.height - _height;

    return Positioned(
      // Giới hạn vị trí trong màn hình
      left: _position.dx.clamp(0, maxX),
      top: _position.dy.clamp(0, maxY),
      child: GestureDetector(
        onPanStart: (_) => setState(() => _isDragging = true),
        onPanUpdate: (details) {
          setState(() {
            _position += details.delta;
          });
        },
        onPanEnd: (_) => setState(() => _isDragging = false),
        child: Material(
          elevation: _isDragging ? 16 : 8,
          borderRadius: BorderRadius.circular(16),
          child: Container(
            width: _width,
            height: _height,
            decoration: BoxDecoration(
              // Sử dụng màu cố định để dễ nhận biết (từ bản FIX thứ hai)
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: const Color(0xff2196f3), // Primary Color
                width: 2,
              ),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.3),
                  blurRadius: 20,
                  offset: const Offset(0, 10),
                ),
              ],
            ),
            child: Column(
              children: [
                // ✅ Header với chức năng kéo thả và nút điều khiển
                _buildHeader(context),

                // ✅ Nội dung Chat (ChatPage)
                Expanded(
                  child: ClipRRect(
                    borderRadius: const BorderRadius.vertical(
                      bottom: Radius.circular(14),
                    ),
                    // Tái sử dụng ChatPage
                    child: ChatPage(
                      arguments: ChatPageArguments(
                        peerId: widget.userId,
                        peerNickname: widget.userName,
                        peerAvatar: widget.avatarUrl,
                      ),
                      isMiniChat:
                          true, // Flag để ChatPage có thể điều chỉnh UI nếu cần
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    // Sử dụng màu cố định để dễ nhận biết (từ bản FIX thứ hai)
    final primaryColor = const Color(0xff2196f3);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: primaryColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(14)),
      ),
      child: Row(
        children: [
          // Drag handle indicator
          Container(
            width: 40,
            height: 4,
            margin: const EdgeInsets.only(right: 8),
            decoration: BoxDecoration(
              color: Colors.white.withOpacity(0.5),
              borderRadius: BorderRadius.circular(2),
            ),
          ),

          // Avatar
          CircleAvatar(
            backgroundImage: widget.avatarUrl.isNotEmpty
                ? NetworkImage(widget.avatarUrl)
                : null,
            radius: 16,
            child: widget.avatarUrl.isEmpty
                ? const Icon(Icons.person, size: 16, color: Colors.grey)
                : null,
          ),
          const SizedBox(width: 8),

          // Name
          Expanded(
            child: Text(
              widget.userName,
              style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.bold,
                fontSize: 14,
              ),
              overflow: TextOverflow.ellipsis,
            ),
          ),

          // Minimize button
          IconButton(
            icon: const Icon(Icons.remove, color: Colors.white, size: 20),
            onPressed: widget.onMinimize,
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
          ),

          // Close button
          IconButton(
            icon: const Icon(Icons.close, color: Colors.white, size: 20),
            onPressed: widget.onClose,
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
          ),
        ],
      ),
    );
  }
}
