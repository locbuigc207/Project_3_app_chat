// lib/services/chat_bubble_service.dart - COMPLETE FIXED VERSION
import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ChatBubbleService {
  static const MethodChannel _channel = MethodChannel('chat_bubble_overlay');
  static const EventChannel _eventChannel = EventChannel('chat_bubble_events');

  static final ChatBubbleService _instance = ChatBubbleService._internal();
  factory ChatBubbleService() => _instance;

  ChatBubbleService._internal() {
    Future.delayed(Duration(milliseconds: 500), () {
      _setupEventListener();
      _restoreBubbles(); // ✅ NEW: Restore on init
    });
  }

  // Stream Controllers
  final _activeBubblesController =
      StreamController<Map<String, BubbleData>>.broadcast();
  Stream<Map<String, BubbleData>> get activeBubblesStream =>
      _activeBubblesController.stream;

  final _bubbleClickController = StreamController<BubbleClickEvent>.broadcast();
  Stream<BubbleClickEvent> get bubbleClickStream =>
      _bubbleClickController.stream;

  final _miniChatMessageController =
      StreamController<MiniChatMessage>.broadcast();
  Stream<MiniChatMessage> get miniChatMessageStream =>
      _miniChatMessageController.stream;

  // State Management
  final Map<String, BubbleData> _activeBubbles = {};
  StreamSubscription? _eventSubscription;
  bool _isInitialized = false;

  DateTime? _lastBubbleOperation;
  static const _minOperationInterval = Duration(milliseconds: 1000);

  // ✅ NEW: Persistence Storage
  SharedPreferences? _prefs;
  static const _storageKey = 'active_bubbles';

  // ===========================================
  // INITIALIZATION & EVENT HANDLING
  // ===========================================

  void _setupEventListener() {
    if (_isInitialized) return;

    try {
      _eventSubscription?.cancel();
      _eventSubscription = _eventChannel.receiveBroadcastStream().listen(
        (event) {
          if (event is Map) {
            final eventType = event['type'] as String?;

            if (eventType == 'click') {
              _handleBubbleClick(event);
            } else if (eventType == 'message') {
              _handleMiniChatMessage(event);
            }
          }
        },
        onError: (error) {
          print('❌ Bubble event stream error: $error');
        },
        cancelOnError: false,
      );
      _isInitialized = true;
      print('✅ Bubble service initialized');
    } catch (e) {
      print('⚠️ Event channel not available: $e');
    }
  }

  void _handleBubbleClick(Map event) {
    final userId = event['userId'] as String?;
    final userName = event['userName'] as String?;
    final avatarUrl = event['avatarUrl'] as String?;

    if (userId != null && !_bubbleClickController.isClosed) {
      _bubbleClickController.add(BubbleClickEvent(
        userId: userId,
        userName: userName ?? '',
        avatarUrl: avatarUrl ?? '',
      ));
    }
  }

  void _handleMiniChatMessage(Map event) {
    final userId = event['userId'] as String?;
    final message = event['message'] as String?;

    if (userId != null &&
        message != null &&
        !_miniChatMessageController.isClosed) {
      _miniChatMessageController.add(MiniChatMessage(
        userId: userId,
        message: message,
        timestamp: DateTime.now(),
      ));
    }
  }

  // ===========================================
  // PERSISTENCE: Save & Restore Bubbles
  // ===========================================

  Future<void> _initPrefs() async {
    _prefs ??= await SharedPreferences.getInstance();
  }

  /// ✅ NEW: Save active bubbles to persistent storage
  Future<void> _saveBubbles() async {
    try {
      await _initPrefs();
      final bubblesJson = _activeBubbles.map(
        (key, value) => MapEntry(key, value.toJson()),
      );
      final jsonString = jsonEncode(bubblesJson);
      await _prefs?.setString(_storageKey, jsonString);
      print('💾 Saved ${_activeBubbles.length} bubbles to storage');
    } catch (e) {
      print('❌ Error saving bubbles: $e');
    }
  }

  /// ✅ NEW: Restore bubbles from storage on app start
  Future<void> _restoreBubbles() async {
    if (!Platform.isAndroid) return;

    try {
      await _initPrefs();
      final jsonString = _prefs?.getString(_storageKey);
      if (jsonString == null || jsonString.isEmpty) {
        print('ℹ️ No saved bubbles to restore');
        return;
      }

      final Map<String, dynamic> bubblesJson = jsonDecode(jsonString);
      print('📦 Restoring ${bubblesJson.length} bubbles...');

      // Check permission first
      final hasPermission = await hasOverlayPermission();
      if (!hasPermission) {
        print('❌ No overlay permission, cannot restore bubbles');
        return;
      }

      int restored = 0;
      for (var entry in bubblesJson.entries) {
        try {
          final bubbleData = BubbleData.fromJson(entry.value);

          // Check if bubble is recent (within 24 hours)
          final age = DateTime.now().difference(bubbleData.timestamp);
          if (age.inHours < 24) {
            final success = await showChatBubble(
              userId: bubbleData.userId,
              userName: bubbleData.userName,
              avatarUrl: bubbleData.avatarUrl,
              lastMessage: bubbleData.lastMessage,
            );
            if (success) restored++;
          }
        } catch (e) {
          print('⚠️ Failed to restore bubble ${entry.key}: $e');
        }
      }

      print('✅ Restored $restored bubbles');
    } catch (e) {
      print('❌ Error restoring bubbles: $e');
    }
  }

  /// ✅ NEW: Clear saved bubbles
  Future<void> clearSavedBubbles() async {
    try {
      await _initPrefs();
      await _prefs?.remove(_storageKey);
      print('🗑️ Cleared saved bubbles');
    } catch (e) {
      print('❌ Error clearing bubbles: $e');
    }
  }

  // ===========================================
  // CORE BUBBLE OPERATIONS
  // ===========================================

  Future<bool> _canPerformOperation() async {
    if (_lastBubbleOperation != null) {
      final elapsed = DateTime.now().difference(_lastBubbleOperation!);
      if (elapsed < _minOperationInterval) {
        final waitTime = _minOperationInterval - elapsed;
        await Future.delayed(waitTime);
      }
    }
    _lastBubbleOperation = DateTime.now();
    return true;
  }

  Future<bool> requestOverlayPermission() async {
    if (!Platform.isAndroid) return false;

    try {
      await _canPerformOperation();
      final bool hasPermission =
          await _channel.invokeMethod('requestPermission');

      if (hasPermission) {
        await Future.delayed(Duration(milliseconds: 500));
      }
      return hasPermission;
    } catch (e) {
      print('❌ Error requesting permission: $e');
      return false;
    }
  }

  Future<bool> hasOverlayPermission() async {
    if (!Platform.isAndroid) return false;

    try {
      final bool hasPermission = await _channel.invokeMethod('hasPermission');
      return hasPermission;
    } catch (e) {
      print('❌ Error checking permission: $e');
      return false;
    }
  }

  Future<bool> showChatBubble({
    required String userId,
    required String userName,
    required String avatarUrl,
    String? lastMessage,
    int maxRetries = 2,
  }) async {
    if (!Platform.isAndroid) return false;

    try {
      await _canPerformOperation();

      final hasPermission = await hasOverlayPermission();
      if (!hasPermission) {
        print('❌ No overlay permission');
        return false;
      }

      if (_activeBubbles.containsKey(userId)) {
        print('ℹ️ Bubble already exists for: $userId');
        return true;
      }

      print('🎈 Creating bubble for: $userName');

      for (int attempt = 0; attempt <= maxRetries; attempt++) {
        try {
          final bool success = await _channel.invokeMethod('showBubble', {
            'userId': userId,
            'userName': userName,
            'avatarUrl': avatarUrl,
            'lastMessage': lastMessage ?? '',
          }).timeout(
            Duration(seconds: 3),
            onTimeout: () => false,
          );

          if (success) {
            final bubbleData = BubbleData(
              userId: userId,
              userName: userName,
              avatarUrl: avatarUrl,
              lastMessage: lastMessage,
              timestamp: DateTime.now(),
            );

            _activeBubbles[userId] = bubbleData;
            if (!_activeBubblesController.isClosed) {
              _activeBubblesController.add(Map.from(_activeBubbles));
            }

            // ✅ NEW: Save to storage
            await _saveBubbles();

            print('✅ Bubble created for: $userName');
            return true;
          }

          if (attempt < maxRetries) {
            await Future.delayed(Duration(milliseconds: 500 * (attempt + 1)));
          }
        } catch (e) {
          if (attempt == maxRetries) rethrow;
          await Future.delayed(Duration(milliseconds: 500 * (attempt + 1)));
        }
      }

      return false;
    } catch (e) {
      print('❌ Error creating bubble: $e');
      return false;
    }
  }

  Future<bool> hideChatBubble(String userId) async {
    if (!Platform.isAndroid) return false;

    try {
      await _canPerformOperation();

      final bool success = await _channel.invokeMethod('hideBubble', {
        'userId': userId,
      });

      if (success) {
        _activeBubbles.remove(userId);
        if (!_activeBubblesController.isClosed) {
          _activeBubblesController.add(Map.from(_activeBubbles));
        }

        // ✅ NEW: Update storage
        await _saveBubbles();

        print('✅ Bubble hidden: $userId');
      }

      return success;
    } catch (e) {
      print('❌ Error hiding bubble: $e');
      return false;
    }
  }

  Future<void> hideAllBubbles() async {
    if (!Platform.isAndroid) return;

    try {
      await _canPerformOperation();
      await _channel.invokeMethod('hideAllBubbles');
      _activeBubbles.clear();
      if (!_activeBubblesController.isClosed) {
        _activeBubblesController.add({});
      }

      // ✅ NEW: Clear storage
      await clearSavedBubbles();

      print('✅ All bubbles hidden');
    } catch (e) {
      print('❌ Error hiding all bubbles: $e');
    }
  }

  // ===========================================
  // ✅ NEW: MINI CHAT OPERATIONS
  // ===========================================

  Future<bool> showMiniChat({
    required String userId,
    required String userName,
    required String avatarUrl,
  }) async {
    if (!Platform.isAndroid) return false;

    try {
      await _canPerformOperation();

      final hasPermission = await hasOverlayPermission();
      if (!hasPermission) return false;

      print('💬 Opening mini chat for: $userName');

      final bool success = await _channel.invokeMethod('showMiniChat', {
        'userId': userId,
        'userName': userName,
        'avatarUrl': avatarUrl,
      }).timeout(
        Duration(seconds: 3),
        onTimeout: () => false,
      );

      if (success) {
        print('✅ Mini chat opened');
      }

      return success;
    } catch (e) {
      print('❌ Error showing mini chat: $e');
      return false;
    }
  }

  Future<bool> hideMiniChat() async {
    if (!Platform.isAndroid) return false;

    try {
      await _canPerformOperation();
      final bool success = await _channel.invokeMethod('hideMiniChat');

      if (success) {
        print('✅ Mini chat hidden');
      }

      return success;
    } catch (e) {
      print('❌ Error hiding mini chat: $e');
      return false;
    }
  }

  Future<void> toggleMiniChat({
    required String userId,
    required String userName,
    required String avatarUrl,
  }) async {
    await hideChatBubble(userId);
    await Future.delayed(Duration(milliseconds: 200));
    await showMiniChat(
      userId: userId,
      userName: userName,
      avatarUrl: avatarUrl,
    );
  }

  // ===========================================
  // UTILITY METHODS
  // ===========================================

  Future<void> updateBubbleMessage({
    required String userId,
    required String message,
  }) async {
    if (_activeBubbles.containsKey(userId)) {
      final bubble = _activeBubbles[userId]!;
      _activeBubbles[userId] = BubbleData(
        userId: bubble.userId,
        userName: bubble.userName,
        avatarUrl: bubble.avatarUrl,
        lastMessage: message,
        timestamp: DateTime.now(),
        unreadCount: bubble.unreadCount + 1,
      );
      if (!_activeBubblesController.isClosed) {
        _activeBubblesController.add(Map.from(_activeBubbles));
      }

      // ✅ NEW: Save updated state
      await _saveBubbles();
    }
  }

  bool isBubbleActive(String userId) => _activeBubbles.containsKey(userId);

  Map<String, BubbleData> get activeBubbles => Map.unmodifiable(_activeBubbles);

  bool get isSupported => Platform.isAndroid;

  void dispose() {
    _eventSubscription?.cancel();
    if (!_activeBubblesController.isClosed) {
      _activeBubblesController.close();
    }
    if (!_bubbleClickController.isClosed) {
      _bubbleClickController.close();
    }
    if (!_miniChatMessageController.isClosed) {
      _miniChatMessageController.close();
    }
    _isInitialized = false;
  }
}

// ===========================================
// DATA MODELS
// ===========================================

class BubbleData {
  final String userId;
  final String userName;
  final String avatarUrl;
  final String? lastMessage;
  final DateTime timestamp;
  final int unreadCount;

  BubbleData({
    required this.userId,
    required this.userName,
    required this.avatarUrl,
    this.lastMessage,
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
      lastMessage: json['lastMessage'],
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

class MiniChatMessage {
  final String userId;
  final String message;
  final DateTime timestamp;

  MiniChatMessage({
    required this.userId,
    required this.message,
    required this.timestamp,
  });
}
