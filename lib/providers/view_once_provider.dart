// lib/providers/view_once_provider.dart
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter_chat_demo/constants/constants.dart';

class ViewOnceProvider {
  final FirebaseFirestore firebaseFirestore;

  ViewOnceProvider({required this.firebaseFirestore});

  // Send view-once message
  Future<bool> sendViewOnceMessage({
    required String groupChatId,
    required String currentUserId,
    required String peerId,
    required String content,
    required int type,
  }) async {
    try {
      final messageId = DateTime.now().millisecondsSinceEpoch.toString();

      await firebaseFirestore
          .collection(FirestoreConstants.pathMessageCollection)
          .doc(groupChatId)
          .collection(groupChatId)
          .doc(messageId)
          .set({
        FirestoreConstants.idFrom: currentUserId,
        FirestoreConstants.idTo: peerId,
        FirestoreConstants.timestamp: messageId,
        FirestoreConstants.content: content,
        FirestoreConstants.type: type,
        'isViewOnce': true,
        'isViewed': false,
        'viewedAt': null,
        'viewedBy': null,
      });

      return true;
    } catch (e) {
      print('Error sending view-once message: $e');
      return false;
    }
  }

  // Mark message as viewed
  Future<bool> markAsViewed({
    required String groupChatId,
    required String messageId,
    required String userId,
  }) async {
    try {
      await firebaseFirestore
          .collection(FirestoreConstants.pathMessageCollection)
          .doc(groupChatId)
          .collection(groupChatId)
          .doc(messageId)
          .update({
        'isViewed': true,
        'viewedAt': DateTime.now().millisecondsSinceEpoch.toString(),
        'viewedBy': userId,
      });

      // Schedule auto-delete after viewing
      Future.delayed(const Duration(seconds: 10), () async {
        await _deleteViewOnceMessage(groupChatId, messageId);
      });

      return true;
    } catch (e) {
      print('Error marking as viewed: $e');
      return false;
    }
  }

  // Delete view-once message
  Future<void> _deleteViewOnceMessage(
      String groupChatId, String messageId) async {
    try {
      await firebaseFirestore
          .collection(FirestoreConstants.pathMessageCollection)
          .doc(groupChatId)
          .collection(groupChatId)
          .doc(messageId)
          .update({
        'isDeleted': true,
        'content': 'This message was opened',
        'deletedAt': DateTime.now().millisecondsSinceEpoch.toString(),
      });
    } catch (e) {
      print('Error deleting view-once message: $e');
    }
  }

  // Check if message is view-once and not viewed
  Future<bool> isViewOnceUnviewed({
    required String groupChatId,
    required String messageId,
  }) async {
    try {
      final doc = await firebaseFirestore
          .collection(FirestoreConstants.pathMessageCollection)
          .doc(groupChatId)
          .collection(groupChatId)
          .doc(messageId)
          .get();

      if (doc.exists) {
        final isViewOnce = doc.data()?['isViewOnce'] ?? false;
        final isViewed = doc.data()?['isViewed'] ?? false;
        return isViewOnce && !isViewed;
      }

      return false;
    } catch (e) {
      print('Error checking view-once status: $e');
      return false;
    }
  }
}

