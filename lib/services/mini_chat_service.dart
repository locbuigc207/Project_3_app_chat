// lib/services/mini_chat_service.dart
import 'dart:io';

import 'package:flutter/services.dart';

/// ✅ Service để show Mini Chat overlay với Flutter widget
class MiniChatService {
  static const MethodChannel _channel = MethodChannel('mini_chat_overlay');

  static final MiniChatService _instance = MiniChatService._internal();
  factory MiniChatService() => _instance;
  MiniChatService._internal();

  /// ✅ Show mini chat với Flutter widget
  Future<bool> showMiniChat({
    required String userId,
    required String userName,
    required String avatarUrl,
  }) async {
    if (!Platform.isAndroid) return false;

    try {
      final bool success = await _channel.invokeMethod('showMiniChat', {
        'userId': userId,
        'userName': userName,
        'avatarUrl': avatarUrl,
      });

      print('✅ Mini chat shown: $success');
      return success;
    } catch (e) {
      print('❌ Error showing mini chat: $e');
      return false;
    }
  }

  /// ✅ Hide mini chat
  Future<bool> hideMiniChat() async {
    if (!Platform.isAndroid) return false;

    try {
      final bool success = await _channel.invokeMethod('hideMiniChat');
      return success;
    } catch (e) {
      print('❌ Error hiding mini chat: $e');
      return false;
    }
  }

  /// ✅ Check if mini chat is showing
  Future<bool> isMiniChatShowing() async {
    if (!Platform.isAndroid) return false;

    try {
      final bool isShowing = await _channel.invokeMethod('isMiniChatShowing');
      return isShowing;
    } catch (e) {
      return false;
    }
  }
}
