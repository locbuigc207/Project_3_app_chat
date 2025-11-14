import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter_chat_demo/constants/constants.dart';

class MessageChat {
  final String idFrom;
  final String idTo;
  final String timestamp;
  final String content;
  final int type;
  final bool isDeleted;
  final String? editedAt;
  final bool isPinned;

  const MessageChat({
    required this.idFrom,
    required this.idTo,
    required this.timestamp,
    required this.content,
    required this.type,
    this.isDeleted = false,
    this.editedAt,
    this.isPinned = false,
  });

  Map<String, dynamic> toJson() {
    return {
      FirestoreConstants.idFrom: idFrom,
      FirestoreConstants.idTo: idTo,
      FirestoreConstants.timestamp: timestamp,
      FirestoreConstants.content: content,
      FirestoreConstants.type: type,
      'isDeleted': isDeleted,
      'editedAt': editedAt,
      'isPinned': isPinned,
    };
  }

  factory MessageChat.fromDocument(DocumentSnapshot doc) {
    return MessageChat(
      idFrom: doc.get(FirestoreConstants.idFrom),
      idTo: doc.get(FirestoreConstants.idTo),
      timestamp: doc.get(FirestoreConstants.timestamp),
      content: doc.get(FirestoreConstants.content),
      type: doc.get(FirestoreConstants.type),
      isDeleted: doc.data().toString().contains('isDeleted')
          ? doc.get('isDeleted')
          : false,
      editedAt: doc.data().toString().contains('editedAt')
          ? doc.get('editedAt')
          : null,
      isPinned: doc.data().toString().contains('isPinned')
          ? doc.get('isPinned')
          : false,
    );
  }
}
