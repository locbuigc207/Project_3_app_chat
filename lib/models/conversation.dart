// lib/models/conversation.dart (Updated)
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter_chat_demo/constants/constants.dart';

class Conversation {
  final String id;
  final bool isGroup;
  final List<String> participants;
  final String lastMessage;
  final String lastMessageTime;
  final int lastMessageType;
  final bool isPinned;
  final String? pinnedAt;
  final bool isMuted;

  const Conversation({
    required this.id,
    required this.isGroup,
    required this.participants,
    required this.lastMessage,
    required this.lastMessageTime,
    required this.lastMessageType,
    this.isPinned = false,
    this.pinnedAt,
    this.isMuted = false,
  });

  Map<String, dynamic> toJson() {
    return {
      FirestoreConstants.isGroup: isGroup,
      FirestoreConstants.participants: participants,
      FirestoreConstants.lastMessage: lastMessage,
      FirestoreConstants.lastMessageTime: lastMessageTime,
      FirestoreConstants.lastMessageType: lastMessageType,
      'isPinned': isPinned,
      'pinnedAt': pinnedAt,
      'isMuted': isMuted,
    };
  }

  factory Conversation.fromDocument(DocumentSnapshot doc) {
    List<String> parts = [];
    try {
      parts = List<String>.from(doc.get(FirestoreConstants.participants));
    } catch (_) {}

    return Conversation(
      id: doc.id,
      isGroup: doc.get(FirestoreConstants.isGroup) ?? false,
      participants: parts,
      lastMessage: doc.get(FirestoreConstants.lastMessage) ?? '',
      lastMessageTime: doc.get(FirestoreConstants.lastMessageTime) ?? '0',
      lastMessageType: doc.get(FirestoreConstants.lastMessageType) ?? 0,
      isPinned: doc.data().toString().contains('isPinned')
          ? doc.get('isPinned')
          : false,
      pinnedAt: doc.data().toString().contains('pinnedAt')
          ? doc.get('pinnedAt')
          : null,
      isMuted: doc.data().toString().contains('isMuted')
          ? doc.get('isMuted')
          : false,
    );
  }
}




