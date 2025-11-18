// lib/utils/error_logger.dart
import 'package:firebase_analytics/firebase_analytics.dart';
import 'package:firebase_crashlytics/firebase_crashlytics.dart';

class ErrorLogger {
  static final FirebaseCrashlytics _crashlytics = FirebaseCrashlytics.instance;
  static final FirebaseAnalytics _analytics = FirebaseAnalytics.instance;

  /// Khởi tạo error logging
  static Future<void> initialize() async {
    // Enable Crashlytics collection
    await _crashlytics.setCrashlyticsCollectionEnabled(true);

    // Pass Flutter errors to Crashlytics
    FlutterError.onError = (errorDetails) {
      _crashlytics.recordFlutterFatalError(errorDetails);
    };

    print('✅ Error logging initialized');
  }

  /// Log error với context
  static Future<void> logError(
    dynamic error,
    StackTrace? stackTrace, {
    String? context,
    Map<String, dynamic>? additionalInfo,
  }) async {
    // Log to console
    print('❌ Error in $context: $error');
    if (stackTrace != null) {
      print('Stack trace: $stackTrace');
    }

    // Set custom keys
    if (context != null) {
      await _crashlytics.setCustomKey('error_context', context);
    }

    if (additionalInfo != null) {
      for (var entry in additionalInfo.entries) {
        await _crashlytics.setCustomKey(entry.key, entry.value.toString());
      }
    }

    // Log to Firebase Crashlytics
    await _crashlytics.recordError(
      error,
      stackTrace,
      reason: context,
      fatal: false,
    );
  }

  /// Log event cho analytics
  static Future<void> logEvent(
    String name,
    Map<String, dynamic>? params,
  ) async {
    try {
      await _analytics.logEvent(
        name: name,
        parameters: params,
      );
      print('📊 Event logged: $name');
    } catch (e) {
      print('❌ Failed to log event: $e');
    }
  }

  /// Log screen view
  static Future<void> logScreenView(String screenName) async {
    await logEvent('screen_view', {
      'screen_name': screenName,
      'timestamp': DateTime.now().millisecondsSinceEpoch,
    });
  }

  /// Set user properties
  static Future<void> setUserId(String userId) async {
    await _crashlytics.setUserIdentifier(userId);
    await _analytics.setUserId(id: userId);
  }

  /// Log message operations
  static Future<void> logMessageSent({
    required String conversationId,
    required int messageType,
  }) async {
    await logEvent('message_sent', {
      'conversation_id': conversationId,
      'message_type': messageType,
    });
  }

  static Future<void> logMessageRead({
    required String conversationId,
  }) async {
    await logEvent('message_read', {
      'conversation_id': conversationId,
    });
  }
}
