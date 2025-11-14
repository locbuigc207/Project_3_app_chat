// lib/providers/conversation_lock_provider.dart
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/services.dart';
import 'package:local_auth/local_auth.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter_chat_demo/constants/constants.dart';

class ConversationLockProvider {
  final FirebaseFirestore firebaseFirestore;
  final SharedPreferences prefs;
  final LocalAuthentication auth = LocalAuthentication();

  ConversationLockProvider({
    required this.firebaseFirestore,
    required this.prefs,
  });

  // Check if device supports biometric
  Future<bool> canCheckBiometrics() async {
    try {
      return await auth.canCheckBiometrics;
    } on PlatformException {
      return false;
    }
  }

  // Get available biometric types
  Future<List<BiometricType>> getAvailableBiometrics() async {
    try {
      return await auth.getAvailableBiometrics();
    } on PlatformException {
      return <BiometricType>[];
    }
  }

  // Authenticate with biometric
  Future<bool> authenticateWithBiometric({
    required String reason,
  }) async {
    try {
      return await auth.authenticate(
        localizedReason: reason,
        options: const AuthenticationOptions(
          stickyAuth: true,
          biometricOnly: false,
        ),
      );
    } on PlatformException catch (e) {
      print('Error authenticating: $e');
      return false;
    }
  }

  // Set PIN for conversation
  Future<bool> setConversationPIN({
    required String conversationId,
    required String pin,
  }) async {
    try {
      // Save encrypted PIN locally
      await prefs.setString('lock_$conversationId', pin);

      // Mark conversation as locked
      await firebaseFirestore
          .collection(FirestoreConstants.pathConversationCollection)
          .doc(conversationId)
          .update({
        'isLocked': true,
        'lockType': 'pin',
        'lockedAt': DateTime.now().millisecondsSinceEpoch.toString(),
      });

      return true;
    } catch (e) {
      print('Error setting PIN: $e');
      return false;
    }
  }

  // Set biometric lock for conversation
  Future<bool> setConversationBiometric({
    required String conversationId,
  }) async {
    try {
      final canUseBiometric = await canCheckBiometrics();
      if (!canUseBiometric) return false;

      await firebaseFirestore
          .collection(FirestoreConstants.pathConversationCollection)
          .doc(conversationId)
          .update({
        'isLocked': true,
        'lockType': 'biometric',
        'lockedAt': DateTime.now().millisecondsSinceEpoch.toString(),
      });

      return true;
    } catch (e) {
      print('Error setting biometric: $e');
      return false;
    }
  }

  // Verify PIN
  Future<bool> verifyPIN({
    required String conversationId,
    required String enteredPin,
  }) async {
    final savedPin = prefs.getString('lock_$conversationId');
    return savedPin != null && savedPin == enteredPin;
  }

  // Remove lock
  Future<bool> removeConversationLock(String conversationId) async {
    try {
      await prefs.remove('lock_$conversationId');

      await firebaseFirestore
          .collection(FirestoreConstants.pathConversationCollection)
          .doc(conversationId)
          .update({
        'isLocked': false,
        'lockType': null,
      });

      return true;
    } catch (e) {
      print('Error removing lock: $e');
      return false;
    }
  }

  // Check if conversation is locked
  Future<Map<String, dynamic>?> getConversationLockStatus(
      String conversationId) async {
    try {
      final doc = await firebaseFirestore
          .collection(FirestoreConstants.pathConversationCollection)
          .doc(conversationId)
          .get();

      if (doc.exists && doc.data()!.containsKey('isLocked')) {
        return {
          'isLocked': doc.get('isLocked') ?? false,
          'lockType': doc.get('lockType'),
        };
      }

      return null;
    } catch (e) {
      print('Error getting lock status: $e');
      return null;
    }
  }
}

