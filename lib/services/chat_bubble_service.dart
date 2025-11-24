import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';

class ChatBubbleService {
  static const MethodChannel _channel = MethodChannel('chat_bubble_overlay');
  static const EventChannel _eventChannel = EventChannel('chat_bubble_events');

  static final ChatBubbleService _instance = ChatBubbleService._internal();
  factory ChatBubbleService() => _instance;

  ChatBubbleService._internal() {
    Future.delayed(Duration(milliseconds: 500), () {
      _setupEventListener();
    });
  }

  final _activeBubblesController =
      StreamController<Map<String, BubbleData>>.broadcast();
  Stream<Map<String, BubbleData>> get activeBubblesStream =>
      _activeBubblesController.stream;

  final _bubbleClickController = StreamController<BubbleClickEvent>.broadcast();
  Stream<BubbleClickEvent> get bubbleClickStream =>
      _bubbleClickController.stream;

  final Map<String, BubbleData> _activeBubbles = {};
  StreamSubscription? _eventSubscription;
  bool _isInitialized = false;

  DateTime? _lastBubbleOperation;
  static const _minOperationInterval =
      Duration(milliseconds: 500); // ✅ Increased delay

  void _setupEventListener() {
    if (_isInitialized) return;

    try {
      _eventSubscription?.cancel();
      _eventSubscription = _eventChannel.receiveBroadcastStream().listen(
        (event) {
          if (event is Map) {
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

  Future<bool> _canPerformOperation() async {
    if (_lastBubbleOperation != null) {
      final elapsed = DateTime.now().difference(_lastBubbleOperation!);
      if (elapsed < _minOperationInterval) {
        await Future.delayed(_minOperationInterval - elapsed);
      }
    }
    _lastBubbleOperation = DateTime.now();
    return true;
  }

  /// ✅ FIX: Request overlay permission with better error handling
  Future<bool> requestOverlayPermission() async {
    if (!Platform.isAndroid) {
      print('ℹ️ Overlay permission only needed on Android');
      return false;
    }

    try {
      await _canPerformOperation();
      print('📱 Requesting overlay permission...');

      final bool hasPermission =
          await _channel.invokeMethod('requestPermission');

      print(hasPermission ? '✅ Permission granted' : '❌ Permission denied');
      return hasPermission;
    } on PlatformException catch (e) {
      print('❌ Error requesting overlay permission: ${e.message}');
      print('💡 Code: ${e.code}, Details: ${e.details}');
      return false;
    } on MissingPluginException {
      print('⚠️ Chat bubble plugin not available on this platform');
      return false;
    } catch (e) {
      print('❌ Unexpected error: $e');
      return false;
    }
  }

  /// ✅ FIX: Check permission with logging
  Future<bool> hasOverlayPermission() async {
    if (!Platform.isAndroid) return false;

    try {
      final bool hasPermission = await _channel.invokeMethod('hasPermission');
      print(hasPermission
          ? '✅ Has overlay permission'
          : '❌ No overlay permission');
      return hasPermission;
    } on PlatformException catch (e) {
      print('❌ Error checking overlay permission: ${e.message}');
      return false;
    } on MissingPluginException {
      print('⚠️ Chat bubble plugin not available');
      return false;
    } catch (e) {
      print('❌ Unexpected error checking permission: $e');
      return false;
    }
  }

  /// ✅ FIX: Show bubble with comprehensive error handling
  Future<bool> showChatBubble({
    required String userId,
    required String userName,
    required String avatarUrl,
    String? lastMessage,
  }) async {
    if (!Platform.isAndroid) {
      print('⚠️ Chat bubbles are only supported on Android');
      return false;
    }

    try {
      // ✅ Rate limiting
      await _canPerformOperation();

      print('🎈 Attempting to show bubble for: $userName');

      // ✅ Check permission first
      final hasPermission = await hasOverlayPermission();
      if (!hasPermission) {
        print('❌ No overlay permission, cannot show bubble');
        print('💡 Call requestOverlayPermission() first');
        return false;
      }

      // ✅ Check if bubble already exists
      if (_activeBubbles.containsKey(userId)) {
        print('ℹ️ Bubble already exists for user: $userId');
        await updateBubbleMessage(userId: userId, message: lastMessage ?? '');
        return true;
      }

      final bubbleData = BubbleData(
        userId: userId,
        userName: userName,
        avatarUrl: avatarUrl,
        lastMessage: lastMessage,
        timestamp: DateTime.now(),
      );

      print('📞 Calling native showBubble method...');
      final bool success = await _channel.invokeMethod('showBubble', {
        'userId': userId,
        'userName': userName,
        'avatarUrl': avatarUrl,
        'lastMessage': lastMessage ?? '',
      });

      if (success) {
        _activeBubbles[userId] = bubbleData;
        if (!_activeBubblesController.isClosed) {
          _activeBubblesController.add(Map.from(_activeBubbles));
        }
        print('✅ Bubble shown successfully for: $userName');
      } else {
        print('❌ Native method returned false');
      }

      return success;
    } on PlatformException catch (e) {
      print('❌ PlatformException showing chat bubble:');
      print('   Code: ${e.code}');
      print('   Message: ${e.message}');
      print('   Details: ${e.details}');

      if (e.code == 'PERMISSION_DENIED') {
        print('💡 Overlay permission was denied. Guide user to settings.');
      }
      return false;
    } on MissingPluginException {
      print('⚠️ Chat bubble plugin not available');
      print('💡 Make sure ChatBubbleService is running');
      return false;
    } catch (e) {
      print('❌ Unexpected error showing bubble: $e');
      return false;
    }
  }

  /// ✅ Hide bubble with rate limiting
  Future<bool> hideChatBubble(String userId) async {
    if (!Platform.isAndroid) return false;

    try {
      await _canPerformOperation();
      print('🗑️ Hiding bubble for: $userId');

      final bool success = await _channel.invokeMethod('hideBubble', {
        'userId': userId,
      });

      if (success) {
        _activeBubbles.remove(userId);
        if (!_activeBubblesController.isClosed) {
          _activeBubblesController.add(Map.from(_activeBubbles));
        }
        print('✅ Bubble hidden for: $userId');
      }

      return success;
    } on PlatformException catch (e) {
      print('❌ Error hiding chat bubble: ${e.message}');
      return false;
    } on MissingPluginException {
      return false;
    } catch (e) {
      print('❌ Unexpected error hiding bubble: $e');
      return false;
    }
  }

  /// Hide all bubbles
  Future<void> hideAllBubbles() async {
    if (!Platform.isAndroid) return;

    try {
      await _canPerformOperation();
      print('🗑️ Hiding all bubbles...');

      await _channel.invokeMethod('hideAllBubbles');
      _activeBubbles.clear();
      if (!_activeBubblesController.isClosed) {
        _activeBubblesController.add({});
      }
      print('✅ All bubbles hidden');
    } on PlatformException catch (e) {
      print('❌ Error hiding all bubbles: ${e.message}');
    } on MissingPluginException {
      // Ignore
    } catch (e) {
      print('❌ Unexpected error: $e');
    }
  }

  /// Update bubble message
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
    }
  }

  /// Check if bubble is active
  bool isBubbleActive(String userId) {
    return _activeBubbles.containsKey(userId);
  }

  /// Get active bubbles
  Map<String, BubbleData> get activeBubbles => Map.unmodifiable(_activeBubbles);

  /// Check if supported
  bool get isSupported => Platform.isAndroid;

  /// Dispose
  void dispose() {
    _eventSubscription?.cancel();
    if (!_activeBubblesController.isClosed) {
      _activeBubblesController.close();
    }
    if (!_bubbleClickController.isClosed) {
      _bubbleClickController.close();
    }
    _isInitialized = false;
    print('🛑 Bubble service disposed');
  }
}

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

  BubbleData copyWith({
    String? userId,
    String? userName,
    String? avatarUrl,
    String? lastMessage,
    DateTime? timestamp,
    int? unreadCount,
  }) {
    return BubbleData(
      userId: userId ?? this.userId,
      userName: userName ?? this.userName,
      avatarUrl: avatarUrl ?? this.avatarUrl,
      lastMessage: lastMessage ?? this.lastMessage,
      timestamp: timestamp ?? this.timestamp,
      unreadCount: unreadCount ?? this.unreadCount,
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
