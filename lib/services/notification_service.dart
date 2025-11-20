// lib/services/notification_service.dart (NEW)
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter_chat_demo/constants/constants.dart';
import 'package:flutter_chat_demo/services/chat_bubble_service.dart';

class NotificationService {
  final ChatBubbleService _bubbleService = ChatBubbleService();
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  // Listen for new messages and auto-create bubbles
  void listenForNewMessages(String currentUserId) {
    _firestore
        .collectionGroup(FirestoreConstants.pathMessageCollection)
        .where(FirestoreConstants.idTo, isEqualTo: currentUserId)
        .where('isRead', isEqualTo: false)
        .snapshots()
        .listen((snapshot) async {
      for (var change in snapshot.docChanges) {
        if (change.type == DocumentChangeType.added) {
          final data = change.doc.data();
          if (data != null) {
            final senderId = data[FirestoreConstants.idFrom] as String;

            // Get sender info
            final senderDoc = await _firestore
                .collection(FirestoreConstants.pathUserCollection)
                .doc(senderId)
                .get();

            if (senderDoc.exists) {
              final senderData = senderDoc.data()!;
              final senderName = senderData[FirestoreConstants.nickname] ?? '';
              final senderAvatar =
                  senderData[FirestoreConstants.photoUrl] ?? '';
              final message = data[FirestoreConstants.content] ?? '';

              // Auto-create bubble if not active
              if (!_bubbleService.isBubbleActive(senderId)) {
                await _bubbleService.showChatBubble(
                  userId: senderId,
                  userName: senderName,
                  avatarUrl: senderAvatar,
                  lastMessage: message,
                );
              }
            }
          }
        }
      }
    });
  }
}
