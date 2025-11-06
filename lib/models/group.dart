import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter_chat_demo/constants/constants.dart';

class Group {
  final String id;
  final String groupName;
  final String groupPhotoUrl;
  final String adminId;
  final List<String> memberIds;
  final String createdAt;

  const Group({
    required this.id,
    required this.groupName,
    required this.groupPhotoUrl,
    required this.adminId,
    required this.memberIds,
    required this.createdAt,
  });

  Map<String, dynamic> toJson() {
    return {
      FirestoreConstants.groupName: groupName,
      FirestoreConstants.groupPhotoUrl: groupPhotoUrl,
      FirestoreConstants.adminId: adminId,
      FirestoreConstants.memberIds: memberIds,
      FirestoreConstants.createdAt: createdAt,
    };
  }

  factory Group.fromDocument(DocumentSnapshot doc) {
    List<String> members = [];
    try {
      members = List<String>.from(doc.get(FirestoreConstants.memberIds));
    } catch (_) {}

    return Group(
      id: doc.id,
      groupName: doc.get(FirestoreConstants.groupName),
      groupPhotoUrl: doc.get(FirestoreConstants.groupPhotoUrl) ?? '',
      adminId: doc.get(FirestoreConstants.adminId),
      memberIds: members,
      createdAt: doc.get(FirestoreConstants.createdAt),
    );
  }
}

class Conversation {
  final String id;
  final bool isGroup;
  final List<String> participants;
  final String lastMessage;
  final String lastMessageTime;
  final int lastMessageType;

  const Conversation({
    required this.id,
    required this.isGroup,
    required this.participants,
    required this.lastMessage,
    required this.lastMessageTime,
    required this.lastMessageType,
  });

  Map<String, dynamic> toJson() {
    return {
      FirestoreConstants.isGroup: isGroup,
      FirestoreConstants.participants: participants,
      FirestoreConstants.lastMessage: lastMessage,
      FirestoreConstants.lastMessageTime: lastMessageTime,
      FirestoreConstants.lastMessageType: lastMessageType,
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
    );
  }
}