// lib/providers/conversation_provider.dart
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter_chat_demo/constants/constants.dart';

class ConversationProvider {
  final FirebaseFirestore firebaseFirestore;

  ConversationProvider({required this.firebaseFirestore});

  // Pin/Unpin conversation
  Future<bool> togglePinConversation(String conversationId, bool currentStatus) async {
    try {
      await firebaseFirestore
          .collection(FirestoreConstants.pathConversationCollection)
          .doc(conversationId)
          .update({
        'isPinned': !currentStatus,
        'pinnedAt': !currentStatus
            ? DateTime.now().millisecondsSinceEpoch.toString()
            : null,
      });
      return true;
    } catch (e) {
      print('Error toggling pin: $e');
      return false;
    }
  }

  // Mute/Unmute conversation
  Future<bool> toggleMuteConversation(String conversationId, bool currentStatus) async {
    try {
      await firebaseFirestore
          .collection(FirestoreConstants.pathConversationCollection)
          .doc(conversationId)
          .update({
        'isMuted': !currentStatus,
      });
      return true;
    } catch (e) {
      print('Error toggling mute: $e');
      return false;
    }
  }

  // Get conversations with pinned ones first
  Stream<QuerySnapshot> getConversationsWithPinned(String userId) {
    return firebaseFirestore
        .collection(FirestoreConstants.pathConversationCollection)
        .where(FirestoreConstants.participants, arrayContains: userId)
        .snapshots()
        .map((snapshot) {
      // Sort: pinned first, then by last message time
      final docs = snapshot.docs;
      docs.sort((a, b) {
        final aData = a.data() as Map<String, dynamic>;
        final bData = b.data() as Map<String, dynamic>;

        final aPinned = aData['isPinned'] ?? false;
        final bPinned = bData['isPinned'] ?? false;

        if (aPinned && !bPinned) return -1;
        if (!aPinned && bPinned) return 1;

        // Both pinned or both not pinned, sort by time
        final aTime = int.parse(aData['lastMessageTime'] ?? '0');
        final bTime = int.parse(bData['lastMessageTime'] ?? '0');
        return bTime.compareTo(aTime);
      });

      return QuerySnapshot.withConverterSnapshot(
        snapshot: snapshot,
        docs: docs,
      );
    });
  }
}
