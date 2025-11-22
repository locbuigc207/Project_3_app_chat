// lib/services/chat_bubble_service.dart - COMPLETE FIXED
import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';

class ChatBubbleService {
  static const MethodChannel _channel = MethodChannel('chat_bubble_overlay');
  static const EventChannel _eventChannel = EventChannel('chat_bubble_events');

  static final ChatBubbleService _instance = ChatBubbleService._internal();
  factory ChatBubbleService() => _instance;
  ChatBubbleService._internal() {
    _setupEventListener();
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

  void _setupEventListener() {
    try {
      _eventSubscription?.cancel();
      _eventSubscription = _eventChannel.receiveBroadcastStream().listen(
        (event) {
          if (event is Map) {
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
        },
        onError: (error) {
          print('❌ Bubble event stream error: $error');
        },
      );
    } catch (e) {
      print('⚠️ Event channel not available: $e');
    }
  }

  /// Request overlay permission (Android only)
  Future<bool> requestOverlayPermission() async {
    if (!Platform.isAndroid) return true;

    try {
      final bool hasPermission =
          await _channel.invokeMethod('requestPermission');
      return hasPermission;
    } on PlatformException catch (e) {
      print('❌ Error requesting overlay permission: ${e.message}');
      return false;
    } on MissingPluginException {
      print('⚠️ Chat bubble plugin not available on this platform');
      return false;
    }
  }

  /// Check if has overlay permission
  Future<bool> hasOverlayPermission() async {
    if (!Platform.isAndroid) return false;

    try {
      final bool hasPermission = await _channel.invokeMethod('hasPermission');
      return hasPermission;
    } on PlatformException catch (e) {
      print('❌ Error checking overlay permission: ${e.message}');
      return false;
    } on MissingPluginException {
      print('⚠️ Chat bubble plugin not available');
      return false;
    }
  }

  /// Show chat bubble
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
      // Check permission first
      final hasPermission = await hasOverlayPermission();
      if (!hasPermission) {
        print('❌ No overlay permission');
        return false;
      }

      // Check if bubble already exists
      if (_activeBubbles.containsKey(userId)) {
        print('ℹ️ Bubble already exists for user: $userId');
        return true;
      }

      final bubbleData = BubbleData(
        userId: userId,
        userName: userName,
        avatarUrl: avatarUrl,
        lastMessage: lastMessage,
        timestamp: DateTime.now(),
      );

      final bool success = await _channel.invokeMethod('showBubble', {
        'userId': userId,
        'userName': userName,
        'avatarUrl': avatarUrl,
        'lastMessage': lastMessage ?? '',
      });

      if (success) {
        _activeBubbles[userId] = bubbleData;
        _activeBubblesController.add(Map.from(_activeBubbles));
        print('✅ Bubble shown for: $userName');
      }

      return success;
    } on PlatformException catch (e) {
      print('❌ Error showing chat bubble: ${e.message}');
      return false;
    } on MissingPluginException {
      print('⚠️ Chat bubble plugin not available');
      return false;
    }
  }

  /// Hide chat bubble
  Future<bool> hideChatBubble(String userId) async {
    if (!Platform.isAndroid) return false;

    try {
      final bool success = await _channel.invokeMethod('hideBubble', {
        'userId': userId,
      });

      if (success) {
        _activeBubbles.remove(userId);
        _activeBubblesController.add(Map.from(_activeBubbles));
        print('✅ Bubble hidden for: $userId');
      }

      return success;
    } on PlatformException catch (e) {
      print('❌ Error hiding chat bubble: ${e.message}');
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  Future<void> hideAllBubbles() async {
    if (!Platform.isAndroid) return;

    try {
      await _channel.invokeMethod('hideAllBubbles');
      _activeBubbles.clear();
      _activeBubblesController.add({});
      print('✅ All bubbles hidden');
    } on PlatformException catch (e) {
      print('❌ Error hiding all bubbles: ${e.message}');
    } on MissingPluginException {
      // Ignore
    }
  }

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
      _activeBubblesController.add(Map.from(_activeBubbles));
    }
  }

  bool isBubbleActive(String userId) {
    return _activeBubbles.containsKey(userId);
  }

  Map<String, BubbleData> get activeBubbles => Map.unmodifiable(_activeBubbles);

  bool get isSupported => Platform.isAndroid;

  void dispose() {
    _eventSubscription?.cancel();
    _activeBubblesController.close();
    _bubbleClickController.close();
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
