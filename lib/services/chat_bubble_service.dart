// lib/services/chat_bubble_service.dart
import 'dart:async';

import 'package:flutter/services.dart';

class ChatBubbleService {
  static const MethodChannel _channel = MethodChannel('chat_bubble_overlay');

  static final ChatBubbleService _instance = ChatBubbleService._internal();
  factory ChatBubbleService() => _instance;
  ChatBubbleService._internal();

  final _activeBubblesController =
      StreamController<Map<String, BubbleData>>.broadcast();
  Stream<Map<String, BubbleData>> get activeBubblesStream =>
      _activeBubblesController.stream;

  final Map<String, BubbleData> _activeBubbles = {};

  // Request overlay permission
  Future<bool> requestOverlayPermission() async {
    try {
      final bool hasPermission =
          await _channel.invokeMethod('requestPermission');
      return hasPermission;
    } catch (e) {
      print('Error requesting overlay permission: $e');
      return false;
    }
  }

  // Check if has overlay permission
  Future<bool> hasOverlayPermission() async {
    try {
      final bool hasPermission = await _channel.invokeMethod('hasPermission');
      return hasPermission;
    } catch (e) {
      print('Error checking overlay permission: $e');
      return false;
    }
  }

  // Show chat bubble
  Future<bool> showChatBubble({
    required String userId,
    required String userName,
    required String avatarUrl,
    String? lastMessage,
  }) async {
    try {
      final hasPermission = await hasOverlayPermission();
      if (!hasPermission) {
        final granted = await requestOverlayPermission();
        if (!granted) return false;
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
        'lastMessage': lastMessage,
      });

      if (success) {
        _activeBubbles[userId] = bubbleData;
        _activeBubblesController.add(_activeBubbles);
      }

      return success;
    } catch (e) {
      print('Error showing chat bubble: $e');
      return false;
    }
  }

  // Hide chat bubble
  Future<bool> hideChatBubble(String userId) async {
    try {
      final bool success = await _channel.invokeMethod('hideBubble', {
        'userId': userId,
      });

      if (success) {
        _activeBubbles.remove(userId);
        _activeBubblesController.add(_activeBubbles);
      }

      return success;
    } catch (e) {
      print('Error hiding chat bubble: $e');
      return false;
    }
  }

  // Hide all bubbles
  Future<void> hideAllBubbles() async {
    try {
      await _channel.invokeMethod('hideAllBubbles');
      _activeBubbles.clear();
      _activeBubblesController.add(_activeBubbles);
    } catch (e) {
      print('Error hiding all bubbles: $e');
    }
  }

  // Check if bubble is active
  bool isBubbleActive(String userId) {
    return _activeBubbles.containsKey(userId);
  }

  // Get active bubbles
  Map<String, BubbleData> get activeBubbles => Map.unmodifiable(_activeBubbles);

  void dispose() {
    _activeBubblesController.close();
  }
}

class BubbleData {
  final String userId;
  final String userName;
  final String avatarUrl;
  final String? lastMessage;
  final DateTime timestamp;

  BubbleData({
    required this.userId,
    required this.userName,
    required this.avatarUrl,
    this.lastMessage,
    required this.timestamp,
  });
}
