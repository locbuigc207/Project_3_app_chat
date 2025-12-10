// lib/services/bubble_service_v2.dart
// ✅ GIAI ĐOẠN 4: BUBBLE API SERVICE

import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// 🎯 SERVICE MỚI - Sử dụng Bubble API (Android 11+)
///
/// Features:
/// - Bubble API notifications thay vì WindowManager overlays
/// - Automatic shortcut management
/// - Better system integration
/// - Improved battery efficiency
class BubbleServiceV2 {
  // ========================================
  // CHANNELS
  // ========================================
  static const MethodChannel _channel = MethodChannel('chat_bubbles_v2');
  static const EventChannel _eventChannel = EventChannel('chat_bubble_events_v2');

  // ========================================
  // SINGLETON
  // ========================================
  static final BubbleServiceV2 _instance = BubbleServiceV2._internal();
  factory BubbleServiceV2() => _instance;

  BubbleServiceV2._internal() {
    _initialize();
  }

  // ========================================
  // STATE
  // ========================================
  bool _isInitialized = false;
  bool _isBubbleApiSupported = false;
  StreamSubscription? _eventSubscription;
  SharedPreferences? _prefs;

  // Active bubbles tracking
  final Map<String, BubbleData> _activeBubbles = {};

  // Stream controllers
  final _bubbleClickController = StreamController<BubbleClickEvent>.broadcast();
  Stream<BubbleClickEvent> get bubbleClickStream => _bubbleClickController.stream;

  final _activeBubblesController = StreamController<Map<String, BubbleData>>.broadcast();
  Stream<Map<String, BubbleData>> get activeBubblesStream => _activeBubblesController.stream;

  // ========================================
  // INITIALIZATION
  // ========================================
  Future<void> _initialize() async {
    if (_isInitialized) return;

    try {
      // Check if Bubble API is supported
      _isBubbleApiSupported = await checkBubbleApiSupport();

      if (!_isBubbleApiSupported) {
        print('⚠️ Bubble API not supported on this device');
        return;
      }

      print('✅ Bubble API is supported');

      // Setup event listener
      _setupEventListener();

      // Initialize SharedPreferences
      _prefs = await SharedPreferences.getInstance();

      // Restore saved bubbles
      await _restoreBubbles();

      _isInitialized = true;
      print('✅ BubbleServiceV2 initialized');
    } catch (e) {
      print('❌ BubbleServiceV2 initialization failed: $e');
    }
  }

  /// Check if device supports Bubble API (Android 11+)
  Future<bool> checkBubbleApiSupport() async {
    if (!Platform.isAndroid) return false;

    try {
      final result = await _channel.invokeMethod<bool>('checkBubbleApiSupport');
      return result ?? false;
    } catch (e) {
      print('❌ Error checking Bubble API support: $e');
      return false;
    }
  }

  /// Setup event listener for bubble interactions
  void _setupEventListener() {
    try {
      _eventSubscription?.cancel();
      _eventSubscription = _eventChannel.receiveBroadcastStream().listen(
            (event) {
          if (event is Map) {
            _handleBubbleEvent(event);
          }
        },
        onError: (error) {
          print('❌ Bubble event error: $error');
        },
      );
      print('✅ Event listener setup complete');
    } catch (e) {
      print('❌ Event listener setup failed: $e');
    }
  }

  void _handleBubbleEvent(Map event) {
    final type = event['type'] as String?;

    if (type == 'click') {
      final userId = event['userId'] as String?;
      final userName = event['userName'] as String?;
      final avatarUrl = event['avatarUrl'] as String?;

      if (userId != null) {
        _bubbleClickController.add(BubbleClickEvent(
          userId: userId,
          userName: userName ?? '',
          avatarUrl: avatarUrl ?? '',
        ));
      }
    }
  }

  // ========================================
  // CORE BUBBLE OPERATIONS
  // ========================================

  /// Show a chat bubble notification
  ///
  /// This creates:
  /// 1. A dynamic shortcut (required for Bubble API)
  /// 2. A bubble notification with the chat interface
  Future<bool> showBubble({
    required String userId,
    required String userName,
    required String message,
    String? avatarUrl,
  }) async {
    if (!_isBubbleApiSupported) {
      print('⚠️ Bubble API not supported, cannot show bubble');
      return false;
    }

    try {
      print('🎈 Showing bubble for: $userName');

      // Check if bubble already exists
      if (_activeBubbles.containsKey(userId)) {
        print('ℹ️ Bubble already exists, updating message');
        return await updateBubble(
          userId: userId,
          message: message,
        );
      }

      // Invoke native method
      final result = await _channel.invokeMethod<bool>('showBubble', {
        'userId': userId,
        'userName': userName,
        'message': message,
        'avatarUrl': avatarUrl ?? '',
      });

      if (result == true) {
        // Track active bubble
        _activeBubbles[userId] = BubbleData(
          userId: userId,
          userName: userName,
          avatarUrl: avatarUrl ?? '',
          lastMessage: message,
          timestamp: DateTime.now(),
        );

        _activeBubblesController.add(Map.from(_activeBubbles));
        await _saveBubbles();

        print('✅ Bubble created successfully');
        return true;
      }

      print('❌ Failed to create bubble');
      return false;
    } catch (e) {
      print('❌ Error showing bubble: $e');
      return false;
    }
  }

  /// Update existing bubble with new message
  Future<bool> updateBubble({
    required String userId,
    required String message,
  }) async {
    if (!_isBubbleApiSupported) return false;

    try {
      final bubble = _activeBubbles[userId];
      if (bubble == null) {
        print('⚠️ Bubble not found: $userId');
        return false;
      }

      final result = await _channel.invokeMethod<bool>('updateBubble', {
        'userId': userId,
        'message': message,
      });

      if (result == true) {
        // Update local state
        _activeBubbles[userId] = BubbleData(
          userId: bubble.userId,
          userName: bubble.userName,
          avatarUrl: bubble.avatarUrl,
          lastMessage: message,
          timestamp: DateTime.now(),
          unreadCount: bubble.unreadCount + 1,
        );

        _activeBubblesController.add(Map.from(_activeBubbles));
        await _saveBubbles();

        print('✅ Bubble updated');
        return true;
      }

      return false;
    } catch (e) {
      print('❌ Error updating bubble: $e');
      return false;
    }
  }

  /// Hide a specific bubble
  Future<bool> hideBubble(String userId) async {
    if (!_isBubbleApiSupported) return false;

    try {
      print('🗑️ Hiding bubble: $userId');

      final result = await _channel.invokeMethod<bool>('hideBubble', {
        'userId': userId,
      });

      if (result == true) {
        _activeBubbles.remove(userId);
        _activeBubblesController.add(Map.from(_activeBubbles));
        await _saveBubbles();

        print('✅ Bubble hidden');
        return true;
      }

      return false;
    } catch (e) {
      print('❌ Error hiding bubble: $e');
      return false;
    }
  }

  /// Hide all active bubbles
  Future<void> hideAllBubbles() async {
    if (!_isBubbleApiSupported) return;

    try {
      await _channel.invokeMethod('hideAllBubbles');

      _activeBubbles.clear();
      _activeBubblesController.add({});
      await _clearSavedBubbles();

      print('✅ All bubbles hidden');
    } catch (e) {
      print('❌ Error hiding all bubbles: $e');
    }
  }

  // ========================================
  // SHORTCUT MANAGEMENT
  // ========================================

  /// Get current shortcut count
  Future<int> getShortcutCount() async {
    try {
      final result = await _channel.invokeMethod<int>('getShortcutCount');
      return result ?? 0;
    } catch (e) {
      print('❌ Error getting shortcut count: $e');
      return 0;
    }
  }

  /// Check if can create more shortcuts (max 5)
  Future<bool> canCreateMoreShortcuts() async {
    try {
      final count = await getShortcutCount();
      return count < 5;
    } catch (e) {
      return false;
    }
  }

  /// Verify shortcut exists for user
  Future<bool> verifyShortcut(String userId) async {
    try {
      final result = await _channel.invokeMethod<bool>('verifyShortcut', {
        'userId': userId,
      });
      return result ?? false;
    } catch (e) {
      print('❌ Error verifying shortcut: $e');
      return false;
    }
  }

  // ========================================
  // PERSISTENCE
  // ========================================

  Future<void> _saveBubbles() async {
    try {
      final bubblesData = _activeBubbles.map(
            (key, value) => MapEntry(key, value.toJson()),
      );

      await _prefs?.setString('bubbles_v2', bubblesData.toString());
      print('💾 Saved ${_activeBubbles.length} bubbles');
    } catch (e) {
      print('❌ Error saving bubbles: $e');
    }
  }

  Future<void> _restoreBubbles() async {
    try {
      final savedData = _prefs?.getString('bubbles_v2');
      if (savedData == null) return;

      // Parse and restore bubbles
      // Implementation depends on your data structure
      print('📦 Restoring saved bubbles');
    } catch (e) {
      print('❌ Error restoring bubbles: $e');
    }
  }

  Future<void> _clearSavedBubbles() async {
    try {
      await _prefs?.remove('bubbles_v2');
      print('🗑️ Cleared saved bubbles');
    } catch (e) {
      print('❌ Error clearing bubbles: $e');
    }
  }

  // ========================================
  // GETTERS
  // ========================================

  bool get isSupported => _isBubbleApiSupported;
  bool get isInitialized => _isInitialized;

  bool isBubbleActive(String userId) => _activeBubbles.containsKey(userId);

  Map<String, BubbleData> get activeBubbles => Map.unmodifiable(_activeBubbles);

  int get activeBubbleCount => _activeBubbles.length;

  // ========================================
  // CLEANUP
  // ========================================

  void dispose() {
    _eventSubscription?.cancel();
    _bubbleClickController.close();
    _activeBubblesController.close();
    _isInitialized = false;
  }
}

// ========================================
// DATA MODELS
// ========================================

class BubbleData {
  final String userId;
  final String userName;
  final String avatarUrl;
  final String lastMessage;
  final DateTime timestamp;
  final int unreadCount;

  BubbleData({
    required this.userId,
    required this.userName,
    required this.avatarUrl,
    required this.lastMessage,
    required this.timestamp,
    this.unreadCount = 0,
  });

  Map<String, dynamic> toJson() {
    return {
      'userId': userId,
      'userName': userName,
      'avatarUrl': avatarUrl,
      'lastMessage': lastMessage,
      'timestamp': timestamp.millisecondsSinceEpoch,
      'unreadCount': unreadCount,
    };
  }

  factory BubbleData.fromJson(Map<String, dynamic> json) {
    return BubbleData(
      userId: json['userId'] ?? '',
      userName: json['userName'] ?? '',
      avatarUrl: json['avatarUrl'] ?? '',
      lastMessage: json['lastMessage'] ?? '',
      timestamp: DateTime.fromMillisecondsSinceEpoch(
        json['timestamp'] ?? DateTime.now().millisecondsSinceEpoch,
      ),
      unreadCount: json['unreadCount'] ?? 0,
    );
  }
}

class BubbleClickEvent {
  final String userId;
  final String userName;
  final String avatarUrl;

  BubbleClickEvent({
    required this.userId,
    required this.userName,
    required this.avatarUrl,
  });
}