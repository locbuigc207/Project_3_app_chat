// lib/providers/conversation_lock_provider.dart (FIXED - No SharedPreferences)
import 'dart:convert';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:crypto/crypto.dart';
import 'package:flutter_chat_demo/constants/constants.dart';

class ConversationLockProvider {
  final FirebaseFirestore firebaseFirestore;

  ConversationLockProvider({required this.firebaseFirestore});

  // Hash PIN for security
  String _hashPIN(String pin) {
    final bytes = utf8.encode(pin);
    final hash = sha256.convert(bytes);
    return hash.toString();
  }

  // Set PIN for conversation (store in Firebase)
  Future<bool> setConversationPIN({
    required String conversationId,
    required String pin,
  }) async {
    try {
      final hashedPin = _hashPIN(pin);

      // Store PIN in separate collection for security
      await firebaseFirestore
          .collection('conversation_locks')
          .doc(conversationId)
          .set({
        'conversationId': conversationId,
        'hashedPin': hashedPin,
        'isLocked': true,
        'failedAttempts': 0,
        'lockedUntil': null,
        'createdAt': FieldValue.serverTimestamp(),
        'updatedAt': FieldValue.serverTimestamp(),
      });

      // Also update conversation document
      await firebaseFirestore
          .collection(FirestoreConstants.pathConversationCollection)
          .doc(conversationId)
          .set({
        'isLocked': true,
        'lockType': 'pin',
        'lockedAt': DateTime.now().millisecondsSinceEpoch.toString(),
      }, SetOptions(merge: true));

      print('✅ PIN lock set in Firebase for: $conversationId');
      return true;
    } catch (e) {
      print('❌ Error setting PIN: $e');
      return false;
    }
  }

  // Verify PIN with attempt tracking
  Future<Map<String, dynamic>> verifyPIN({
    required String conversationId,
    required String enteredPin,
  }) async {
    try {
      final lockDoc = await firebaseFirestore
          .collection('conversation_locks')
          .doc(conversationId)
          .get();

      if (!lockDoc.exists) {
        return {
          'success': false,
          'message': 'No PIN set for this conversation',
          'failedAttempts': 0,
        };
      }

      final data = lockDoc.data()!;
      final savedHashedPin = data['hashedPin'] as String;
      final failedAttempts = (data['failedAttempts'] as int?) ?? 0;
      final lockedUntil = data['lockedUntil'] as Timestamp?;

      // Check if temporarily locked
      if (lockedUntil != null) {
        final now = DateTime.now();
        final unlockTime = lockedUntil.toDate();
        if (now.isBefore(unlockTime)) {
          final remainingMinutes = unlockTime.difference(now).inMinutes;
          return {
            'success': false,
            'message':
                'Too many failed attempts. Try again in $remainingMinutes minutes.',
            'failedAttempts': failedAttempts,
            'locked': true,
          };
        }
      }

      // Verify PIN
      final enteredHashedPin = _hashPIN(enteredPin);
      final isCorrect = enteredHashedPin == savedHashedPin;

      if (isCorrect) {
        // Reset failed attempts on success
        await firebaseFirestore
            .collection('conversation_locks')
            .doc(conversationId)
            .update({
          'failedAttempts': 0,
          'lockedUntil': null,
          'lastAccessedAt': FieldValue.serverTimestamp(),
        });

        print('✅ PIN verified successfully');
        return {
          'success': true,
          'message': 'PIN correct',
          'failedAttempts': 0,
        };
      } else {
        // Increment failed attempts
        final newFailedAttempts = failedAttempts + 1;

        Map<String, dynamic> updateData = {
          'failedAttempts': newFailedAttempts,
          'updatedAt': FieldValue.serverTimestamp(),
        };

        // Lock for 30 minutes after 5 failed attempts
        if (newFailedAttempts >= 5) {
          final lockUntil = DateTime.now().add(Duration(minutes: 30));
          updateData['lockedUntil'] = Timestamp.fromDate(lockUntil);
        }

        await firebaseFirestore
            .collection('conversation_locks')
            .doc(conversationId)
            .update(updateData);

        print('❌ PIN incorrect. Failed attempts: $newFailedAttempts');

        return {
          'success': false,
          'message': newFailedAttempts >= 5
              ? 'Too many failed attempts. Locked for 30 minutes.'
              : 'Incorrect PIN. ${5 - newFailedAttempts} attempts remaining.',
          'failedAttempts': newFailedAttempts,
          'locked': newFailedAttempts >= 5,
        };
      }
    } catch (e) {
      print('❌ Error verifying PIN: $e');
      return {
        'success': false,
        'message': 'Error verifying PIN',
        'failedAttempts': 0,
      };
    }
  }

  // Auto-delete messages after 5 failed attempts
  Future<void> autoDeleteMessagesAfterFailedAttempts({
    required String conversationId,
  }) async {
    try {
      print('🗑️ Auto-deleting messages due to failed PIN attempts...');

      // Get all messages in conversation
      final messagesSnapshot = await firebaseFirestore
          .collection(FirestoreConstants.pathMessageCollection)
          .doc(conversationId)
          .collection(conversationId)
          .get();

      // Batch delete
      final batch = firebaseFirestore.batch();

      for (var doc in messagesSnapshot.docs) {
        batch.update(doc.reference, {
          'isDeleted': true,
          'content': 'Messages deleted due to security breach',
          'deletedAt': DateTime.now().millisecondsSinceEpoch.toString(),
        });
      }

      await batch.commit();
      print('✅ All messages auto-deleted');

      // Update lock status
      await firebaseFirestore
          .collection('conversation_locks')
          .doc(conversationId)
          .update({
        'messagesAutoDeleted': true,
        'autoDeletedAt': FieldValue.serverTimestamp(),
      });
    } catch (e) {
      print('❌ Error auto-deleting messages: $e');
    }
  }

  // Remove lock
  Future<bool> removeConversationLock(String conversationId) async {
    try {
      // Delete from locks collection
      await firebaseFirestore
          .collection('conversation_locks')
          .doc(conversationId)
          .delete();

      // Update conversation document
      await firebaseFirestore
          .collection(FirestoreConstants.pathConversationCollection)
          .doc(conversationId)
          .set({
        'isLocked': false,
        'lockType': null,
      }, SetOptions(merge: true));

      print('✅ Lock removed from Firebase');
      return true;
    } catch (e) {
      print('❌ Error removing lock: $e');
      return false;
    }
  }

  // Check if conversation is locked
  Future<Map<String, dynamic>?> getConversationLockStatus(
      String conversationId) async {
    try {
      final lockDoc = await firebaseFirestore
          .collection('conversation_locks')
          .doc(conversationId)
          .get();

      if (lockDoc.exists) {
        final data = lockDoc.data()!;
        final lockedUntil = data['lockedUntil'] as Timestamp?;

        bool isTemporarilyLocked = false;
        if (lockedUntil != null) {
          isTemporarilyLocked = DateTime.now().isBefore(lockedUntil.toDate());
        }

        return {
          'isLocked': data['isLocked'] ?? true,
          'lockType': 'pin',
          'failedAttempts': data['failedAttempts'] ?? 0,
          'temporarilyLocked': isTemporarilyLocked,
          'lockedUntil': lockedUntil?.toDate(),
        };
      }

      // Check conversation document as fallback
      final convDoc = await firebaseFirestore
          .collection(FirestoreConstants.pathConversationCollection)
          .doc(conversationId)
          .get();

      if (convDoc.exists) {
        final data = convDoc.data();
        if (data != null && data.containsKey('isLocked')) {
          return {
            'isLocked': data['isLocked'] ?? false,
            'lockType': data['lockType'] ?? 'pin',
            'failedAttempts': 0,
            'temporarilyLocked': false,
          };
        }
      }

      return null;
    } catch (e) {
      print('❌ Error getting lock status: $e');
      return null;
    }
  }

  // Get failed attempts count
  Future<int> getFailedAttempts(String conversationId) async {
    try {
      final lockDoc = await firebaseFirestore
          .collection('conversation_locks')
          .doc(conversationId)
          .get();

      if (lockDoc.exists) {
        return (lockDoc.data()?['failedAttempts'] as int?) ?? 0;
      }
      return 0;
    } catch (e) {
      print('❌ Error getting failed attempts: $e');
      return 0;
    }
  }
}
