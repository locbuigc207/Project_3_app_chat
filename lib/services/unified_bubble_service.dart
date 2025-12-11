// lib/services/unified_bubble_service.dart
// ✅ GIAI ĐOẠN 4 & 7: UNIFIED SERVICE - Auto-select best API & Message Operations

import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart'; // Thêm để sử dụng MethodChannel cho GIAI ĐOẠN 7
import 'package:flutter_chat_demo/services/bubble_service_v2.dart';
import 'package:flutter_chat_demo/services/chat_bubble_service.dart';

/// 🎯 UNIFIED SERVICE - Automatically uses best API for device
///
/// Strategy:
/// - Android 11+ (API 30+): Use Bubble API (BubbleServiceV2)
/// - Android < 11: Use WindowManager (ChatBubbleService)
///
/// This provides:
/// - Seamless migration path
/// - Automatic API selection
/// - Unified interface for app code
class UnifiedBubbleService {
  // ========================================
  // SERVICES
  // ========================================
  late final BubbleServiceV2 _bubbleApiService;
  late final ChatBubbleService _windowManagerService;

  // ========================================
  // SINGLETON
  // ========================================
  static final UnifiedBubbleService _instance =
      UnifiedBubbleService._internal();
  factory UnifiedBubbleService() => _instance;

  UnifiedBubbleService._internal() {
    _bubbleApiService = BubbleServiceV2();
    _windowManagerService = ChatBubbleService();
    _initialize();
  }

  // ========================================
  // STATE
  // ========================================
  bool _isInitialized = false;
  BubbleImplementation _currentImplementation = BubbleImplementation.unknown;

  // Forwarded streams
  StreamController<BubbleClickEvent>? _clickController;
  StreamController<Map<String, dynamic>>? _bubblesController;

  Stream<BubbleClickEvent> get bubbleClickStream {
    return _clickController?.stream ?? Stream.empty();
  }

  Stream<Map<String, dynamic>> get activeBubblesStream {
    return _bubblesController?.stream ?? Stream.empty();
  }

  // ========================================
  // INITIALIZATION
  // ========================================
  Future<void> _initialize() async {
    if (_isInitialized) return;

    try {
      // Detect best implementation
      _currentImplementation = await _detectBestImplementation();

      print('✅ Using implementation: ${_currentImplementation.name}');

      // Setup stream forwarding
      _setupStreamForwarding();

      _isInitialized = true;
      print('✅ UnifiedBubbleService initialized');
    } catch (e) {
      print('❌ UnifiedBubbleService initialization failed: $e');
    }
  }

  /// Detect which implementation to use
  Future<BubbleImplementation> _detectBestImplementation() async {
    if (!Platform.isAndroid) {
      return BubbleImplementation.none;
    }

    // Check if Bubble API is supported
    final supportsBubbleApi = await _bubbleApiService.checkBubbleApiSupport();

    if (supportsBubbleApi) {
      print('✅ Device supports Bubble API');
      return BubbleImplementation.bubbleApi;
    }

    // Fallback to WindowManager
    print('⚠️ Falling back to WindowManager');
    return BubbleImplementation.windowManager;
  }

  /// Setup stream forwarding from active service
  void _setupStreamForwarding() {
    _clickController = StreamController<BubbleClickEvent>.broadcast();
    _bubblesController = StreamController<Map<String, dynamic>>.broadcast();

    if (_currentImplementation == BubbleImplementation.bubbleApi) {
      // Forward from Bubble API service
      _bubbleApiService.bubbleClickStream.listen(
        (event) => _clickController?.add(event),
      );

      _bubbleApiService.activeBubblesStream.listen(
        (bubbles) {
          final converted = bubbles.map(
            (key, value) => MapEntry(key, value.toJson()),
          );
          _bubblesController?.add(converted);
        },
      );
    } else if (_currentImplementation == BubbleImplementation.windowManager) {
      // Forward from WindowManager service
      _windowManagerService.bubbleClickStream.listen(
        (event) => _clickController?.add(BubbleClickEvent(
          userId: event.userId,
          userName: event.userName,
          avatarUrl: event.avatarUrl,
        )),
      );

      _windowManagerService.activeBubblesStream.listen(
        (bubbles) {
          final converted = bubbles.map(
            (key, value) => MapEntry(key, value.toJson()),
          );
          _bubblesController?.add(converted);
        },
      );
    }
  }

  // ========================================
  // UNIFIED API
  // ========================================

  /// Check if overlay permission is granted
  Future<bool> hasOverlayPermission() async {
    if (_currentImplementation == BubbleImplementation.bubbleApi) {
      // Bubble API doesn't need overlay permission
      return true;
    }

    return await _windowManagerService.hasOverlayPermission();
  }

  /// Request overlay permission (only for WindowManager)
  Future<bool> requestOverlayPermission() async {
    if (_currentImplementation == BubbleImplementation.bubbleApi) {
      // Bubble API doesn't need overlay permission
      return true;
    }

    return await _windowManagerService.requestOverlayPermission();
  }

  /// Show a chat bubble
  Future<bool> showChatBubble({
    required String userId,
    required String userName,
    required String avatarUrl,
    String? lastMessage,
  }) async {
    if (_currentImplementation == BubbleImplementation.bubbleApi) {
      return await _bubbleApiService.showBubble(
        userId: userId,
        userName: userName,
        message: lastMessage ?? 'New message',
        avatarUrl: avatarUrl,
      );
    } else if (_currentImplementation == BubbleImplementation.windowManager) {
      return await _windowManagerService.showChatBubble(
        userId: userId,
        userName: userName,
        avatarUrl: avatarUrl,
        lastMessage: lastMessage,
      );
    }

    return false;
  }

  /// Update bubble with new message
  Future<void> updateBubbleMessage({
    required String userId,
    required String message,
  }) async {
    if (_currentImplementation == BubbleImplementation.bubbleApi) {
      await _bubbleApiService.updateBubble(
        userId: userId,
        message: message,
      );
    } else if (_currentImplementation == BubbleImplementation.windowManager) {
      await _windowManagerService.updateBubbleMessage(
        userId: userId,
        message: message,
      );
    }
  }

  /// Hide a specific bubble
  Future<bool> hideChatBubble(String userId) async {
    if (_currentImplementation == BubbleImplementation.bubbleApi) {
      return await _bubbleApiService.hideBubble(userId);
    } else if (_currentImplementation == BubbleImplementation.windowManager) {
      return await _windowManagerService.hideChatBubble(userId);
    }

    return false;
  }

  /// Hide all bubbles
  Future<void> hideAllBubbles() async {
    if (_currentImplementation == BubbleImplementation.bubbleApi) {
      await _bubbleApiService.hideAllBubbles();
    } else if (_currentImplementation == BubbleImplementation.windowManager) {
      await _windowManagerService.hideAllBubbles();
    }
  }

  /// Show mini chat window
  Future<bool> showMiniChat({
    required String userId,
    required String userName,
    required String avatarUrl,
  }) async {
    // Mini chat only works with WindowManager for now
    // TODO: Implement mini chat for Bubble API
    if (_currentImplementation == BubbleImplementation.windowManager) {
      return await _windowManagerService.showMiniChat(
        userId: userId,
        userName: userName,
        avatarUrl: avatarUrl,
      );
    }

    print('⚠️ Mini chat not supported with Bubble API yet');
    return false;
  }

  /// Hide mini chat
  Future<bool> hideMiniChat() async {
    if (_currentImplementation == BubbleImplementation.windowManager) {
      return await _windowManagerService.hideMiniChat();
    }

    return false;
  }

  // ========================================
  // UTILITY METHODS
  // ========================================

  /// Check if bubble is active
  bool isBubbleActive(String userId) {
    if (_currentImplementation == BubbleImplementation.bubbleApi) {
      return _bubbleApiService.isBubbleActive(userId);
    } else if (_currentImplementation == BubbleImplementation.windowManager) {
      return _windowManagerService.isBubbleActive(userId);
    }

    return false;
  }

  /// Get active bubble count
  int getActiveBubbleCount() {
    if (_currentImplementation == BubbleImplementation.bubbleApi) {
      return _bubbleApiService.activeBubbleCount;
    } else if (_currentImplementation == BubbleImplementation.windowManager) {
      return _windowManagerService.activeBubbles.length;
    }

    return 0;
  }

  String getImplementationInfo() {
    switch (_currentImplementation) {
      case BubbleImplementation.bubbleApi:
        return 'Bubble API (Android 11+)';
      case BubbleImplementation.windowManager:
        return 'WindowManager (Android < 11)';
      case BubbleImplementation.none:
        return 'Not supported';
      case BubbleImplementation.unknown:
        return 'Detecting...';
    }
  }

  bool get isSupported {
    return _currentImplementation != BubbleImplementation.none;
  }

  BubbleImplementation get currentImplementation => _currentImplementation;

  Future<bool> migrateToModernApi() async {
    if (_currentImplementation == BubbleImplementation.bubbleApi) {
      print('✅ Already using Bubble API');
      return true;
    }

    // Check if can migrate
    final supportsBubbleApi = await _bubbleApiService.checkBubbleApiSupport();
    if (!supportsBubbleApi) {
      print('⚠️ Cannot migrate: Bubble API not supported');
      return false;
    }

    print('🔄 Migrating to Bubble API...');

    try {
      // Get current bubbles from WindowManager
      final currentBubbles = _windowManagerService.activeBubbles;

      // Hide all WindowManager bubbles
      await _windowManagerService.hideAllBubbles();

      // Switch implementation
      _currentImplementation = BubbleImplementation.bubbleApi;
      _setupStreamForwarding();

      // Recreate bubbles with Bubble API
      for (var bubble in currentBubbles.values) {
        await _bubbleApiService.showBubble(
          userId: bubble.userId,
          userName: bubble.userName,
          message: bubble.lastMessage ?? 'New message',
          avatarUrl: bubble.avatarUrl,
        );
      }

      print('✅ Migration complete');
      return true;
    } catch (e) {
      print('❌ Migration failed: $e');
      return false;
    }
  }

  Future<bool> sendMessage({
    required String userId,
    required String userName,
    required String message,
    required String avatarUrl,
    String messageType = 'text', // 'text', 'image', 'voice', 'location'
  }) async {
    if (_currentImplementation != BubbleImplementation.bubbleApi) {
      print('⚠️ Send message only supported with Bubble API');
      return false;
    }

    try {
      final result = await const MethodChannel('chat_bubbles_v2')
          .invokeMethod<bool>('sendMessage', {
        'userId': userId,
        'userName': userName,
        'message': message,
        'avatarUrl': avatarUrl,
        'messageType': messageType,
      });

      if (result == true) {
        print('✅ Message sent to bubble: $message');
      }

      return result ?? false;
    } catch (e) {
      print('❌ Error sending message: $e');
      return false;
    }
  }

  /// Get message count for a bubble conversation
  Future<int> getMessageCount(String userId) async {
    if (_currentImplementation != BubbleImplementation.bubbleApi) {
      return 0;
    }

    try {
      final result = await const MethodChannel('chat_bubbles_v2')
          .invokeMethod<int>('getMessageCount', {
        'userId': userId,
      });

      return result ?? 0;
    } catch (e) {
      print('❌ Error getting message count: $e');
      return 0;
    }
  }

  Future<Map<String, dynamic>> getBubbleStats() async {
    try {
      final result = await const MethodChannel('chat_bubbles_v2')
          .invokeMethod<Map>('getBubbleStats');

      return result?.cast<String, dynamic>() ?? {};
    } catch (e) {
      print('❌ Error getting bubble stats: $e');
      return {};
    }
  }

  /// Clear message history for a user
  Future<bool> clearMessageHistory(String userId) async {
    if (_currentImplementation != BubbleImplementation.bubbleApi) {
      return false;
    }

    try {
      final result = await const MethodChannel('chat_bubbles_v2')
          .invokeMethod<bool>('clearMessageHistory', {
        'userId': userId,
      });

      if (result == true) {
        print('✅ Message history cleared for: $userId');
      }

      return result ?? false;
    } catch (e) {
      print('❌ Error clearing history: $e');
      return false;
    }
  }

  Future<void> logBubbleState() async {
    try {
      await const MethodChannel('chat_bubbles_v2')
          .invokeMethod('logBubbleState');
      print('✅ Bubble state logged (check Android logs)');
    } catch (e) {
      print('❌ Error logging bubble state: $e');
    }
  }

  /// Helper to determine message type from content
  String _getMessageType(String content, int typeCode) {
    // typeCode from TypeMessage constants:
    // 0 = text, 1 = image, 2 = sticker, 3 = voice, 4 = location
    switch (typeCode) {
      case 1:
        return 'image';
      case 3:
        return 'voice';
      case 4:
        return 'location';
      default:
        // Check if content contains location data
        if (content.contains('maps.google.com') ||
            content.contains('Location:')) {
          return 'location';
        }
        return 'text';
    }
  }

  void dispose() {
    _bubbleApiService.dispose();
    _windowManagerService.dispose();
    _clickController?.close();
    _bubblesController?.close();
    _isInitialized = false;
  }
}

enum BubbleImplementation {
  bubbleApi, // Android 11+ Bubble API
  windowManager, // Legacy WindowManager overlays
  none, // Not supported (iOS, etc)
  unknown, // Not yet detected
}
