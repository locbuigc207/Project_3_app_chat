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
  final bool isRead; // THÊM FIELD MỚI
  final String? readAt; // THÊM FIELD MỚI

  const MessageChat({
    required this.idFrom,
    required this.idTo,
    required this.timestamp,
    required this.content,
    required this.type,
    this.isDeleted = false,
    this.editedAt,
    this.isPinned = false,
    this.isRead = false, // THÊM FIELD MỚI
    this.readAt, // THÊM FIELD MỚI
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
      'isRead': isRead, // THÊM FIELD MỚI
      'readAt': readAt, // THÊM FIELD MỚI
    };
  }

  factory MessageChat.fromDocument(DocumentSnapshot doc) {
    final data = doc.data() as Map<String, dynamic>?;

    return MessageChat(
      idFrom: doc.get(FirestoreConstants.idFrom),
      idTo: doc.get(FirestoreConstants.idTo),
      timestamp: doc.get(FirestoreConstants.timestamp),
      content: doc.get(FirestoreConstants.content),
      type: doc.get(FirestoreConstants.type),
      isDeleted:
          data?.containsKey('isDeleted') == true ? doc.get('isDeleted') : false,
      editedAt:
          data?.containsKey('editedAt') == true ? doc.get('editedAt') : null,
      isPinned:
          data?.containsKey('isPinned') == true ? doc.get('isPinned') : false,
      isRead: data?.containsKey('isRead') == true // THÊM FIELD MỚI
          ? doc.get('isRead')
          : false,
      readAt: data?.containsKey('readAt') == true // THÊM FIELD MỚI
          ? doc.get('readAt')
          : null,
    );
  }
}
