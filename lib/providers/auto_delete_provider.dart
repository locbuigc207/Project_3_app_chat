// lib/providers/auto_delete_provider.dart
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter_chat_demo/constants/constants.dart';

enum AutoDeleteDuration {
  never,
  oneDay,
  sevenDays,
  thirtyDays,
  custom,
}

class AutoDeleteProvider {
  final FirebaseFirestore firebaseFirestore;

  AutoDeleteProvider({required this.firebaseFirestore});

  // Set auto-delete for conversation
  Future<bool> setAutoDelete({
    required String conversationId,
    required AutoDeleteDuration duration,
    int? customHours,
  }) async {
    try {
      int? deleteAfterMillis;

      switch (duration) {
        case AutoDeleteDuration.oneDay:
          deleteAfterMillis = 24 * 60 * 60 * 1000;
          break;
        case AutoDeleteDuration.sevenDays:
          deleteAfterMillis = 7 * 24 * 60 * 60 * 1000;
          break;
        case AutoDeleteDuration.thirtyDays:
          deleteAfterMillis = 30 * 24 * 60 * 60 * 1000;
          break;
        case AutoDeleteDuration.custom:
          if (customHours != null) {
            deleteAfterMillis = customHours * 60 * 60 * 1000;
          }
          break;
        case AutoDeleteDuration.never:
          deleteAfterMillis = null;
          break;
      }

      await firebaseFirestore
          .collection(FirestoreConstants.pathConversationCollection)
          .doc(conversationId)
          .update({
        'autoDeleteEnabled': duration != AutoDeleteDuration.never,
        'autoDeleteDuration': deleteAfterMillis,
        'autoDeleteUpdatedAt':
        DateTime.now().millisecondsSinceEpoch.toString(),
      });

      return true;
    } catch (e) {
      print('Error setting auto-delete: $e');
      return false;
    }
  }

  // Mark message for auto-deletion
  Future<void> markMessageForDeletion({
    required String groupChatId,
    required String messageId,
    required int deleteAfterMillis,
  }) async {
    try {
      final deleteAt = DateTime.now().millisecondsSinceEpoch + deleteAfterMillis;

      await firebaseFirestore
          .collection(FirestoreConstants.pathMessageCollection)
          .doc(groupChatId)
          .collection(groupChatId)
          .doc(messageId)
          .update({
        'autoDeleteAt': deleteAt.toString(),
      });
    } catch (e) {
      print('Error marking message for deletion: $e');
    }
  }

  // Check and delete expired messages
  Future<void> deleteExpiredMessages(String groupChatId) async {
    try {
      final now = DateTime.now().millisecondsSinceEpoch;

      final expiredMessages = await firebaseFirestore
          .collection(FirestoreConstants.pathMessageCollection)
          .doc(groupChatId)
          .collection(groupChatId)
          .where('autoDeleteAt', isLessThan: now.toString())
          .get();

      final batch = firebaseFirestore.batch();

      for (var doc in expiredMessages.docs) {
        // Soft delete
        batch.update(doc.reference, {
          'isDeleted': true,
          'content': 'This message was automatically deleted',
          'deletedAt': now.toString(),
        });
      }

      await batch.commit();
    } catch (e) {
      print('Error deleting expired messages: $e');
    }
  }

  // Get auto-delete settings for conversation
  Future<Map<String, dynamic>?> getAutoDeleteSettings(
      String conversationId) async {
    try {
      final doc = await firebaseFirestore
          .collection(FirestoreConstants.pathConversationCollection)
          .doc(conversationId)
          .get();

      if (doc.exists && doc.data()!.containsKey('autoDeleteEnabled')) {
        return {
          'enabled': doc.get('autoDeleteEnabled') ?? false,
          'duration': doc.get('autoDeleteDuration'),
        };
      }

      return null;
    } catch (e) {
      print('Error getting auto-delete settings: $e');
      return null;
    }
  }

  // Schedule message deletion on send
  Future<void> scheduleMessageDeletion({
    required String groupChatId,
    required String messageId,
    required String conversationId,
  }) async {
    try {
      final settings = await getAutoDeleteSettings(conversationId);

      if (settings != null &&
          settings['enabled'] == true &&
          settings['duration'] != null) {
        await markMessageForDeletion(
          groupChatId: groupChatId,
          messageId: messageId,
          deleteAfterMillis: settings['duration'] as int,
        );
      }
    } catch (e) {
      print('Error scheduling message deletion: $e');
    }
  }
}

