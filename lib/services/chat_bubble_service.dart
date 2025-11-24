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
      Duration(milliseconds: 1000); // ✅ Tăng lên 1s

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
        final waitTime = _minOperationInterval - elapsed;
        print('⏳ Waiting ${waitTime.inMilliseconds}ms before next operation');
        await Future.delayed(waitTime);
      }
    }
    _lastBubbleOperation = DateTime.now();
    return true;
  }

  /// ✅ FIX 1: Better permission handling
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

      if (hasPermission) {
        print('✅ Permission granted');
        // ✅ Đợi thêm để system xử lý permission
        await Future.delayed(Duration(milliseconds: 500));
      } else {
        print('❌ Permission denied');
      }

      return hasPermission;
    } on PlatformException catch (e) {
      print('❌ Error requesting overlay permission: ${e.message}');
      return false;
    } catch (e) {
      print('❌ Unexpected error: $e');
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

  /// ✅ FIX 2: Improved bubble creation with retry
  Future<bool> showChatBubble({
    required String userId,
    required String userName,
    required String avatarUrl,
    String? lastMessage,
    int maxRetries = 2,
  }) async {
    if (!Platform.isAndroid) {
      print('⚠️ Chat bubbles only supported on Android');
      return false;
    }

    try {
      await _canPerformOperation();

      // ✅ Check permission first
      final hasPermission = await hasOverlayPermission();
      if (!hasPermission) {
        print('❌ No overlay permission');
        return false;
      }

      // ✅ Check if already exists
      if (_activeBubbles.containsKey(userId)) {
        print('ℹ️ Bubble already exists for: $userId');
        return true;
      }

      print('🎈 Creating bubble for: $userName');

      // ✅ Retry mechanism
      for (int attempt = 0; attempt <= maxRetries; attempt++) {
        try {
          final bool success = await _channel.invokeMethod('showBubble', {
            'userId': userId,
            'userName': userName,
            'avatarUrl': avatarUrl,
            'lastMessage': lastMessage ?? '',
          }).timeout(
            Duration(seconds: 3),
            onTimeout: () {
              print('⏱️ Bubble creation timeout on attempt ${attempt + 1}');
              return false;
            },
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

            print('✅ Bubble created successfully for: $userName');
            return true;
          }

          if (attempt < maxRetries) {
            print('🔄 Retrying bubble creation (${attempt + 1}/$maxRetries)');
            await Future.delayed(Duration(milliseconds: 500 * (attempt + 1)));
          }
        } catch (e) {
          print('❌ Attempt ${attempt + 1} failed: $e');
          if (attempt == maxRetries) rethrow;
          await Future.delayed(Duration(milliseconds: 500 * (attempt + 1)));
        }
      }

      return false;
    } on PlatformException catch (e) {
      print('❌ PlatformException: ${e.code} - ${e.message}');
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
      print('✅ All bubbles hidden');
    } catch (e) {
      print('❌ Error hiding all bubbles: $e');
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
      if (!_activeBubblesController.isClosed) {
        _activeBubblesController.add(Map.from(_activeBubbles));
      }
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
