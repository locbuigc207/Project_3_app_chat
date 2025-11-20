// lib/providers/voice_message_provider.dart - NEW
import 'dart:io';

import 'package:firebase_storage/firebase_storage.dart';
import 'package:flutter_sound/flutter_sound.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';

class VoiceMessageProvider {
  final FirebaseStorage firebaseStorage;
  final FlutterSoundRecorder _recorder = FlutterSoundRecorder();
  final FlutterSoundPlayer _player = FlutterSoundPlayer();

  bool _isRecorderInitialized = false;
  bool _isPlayerInitialized = false;
  String? _currentRecordingPath;

  VoiceMessageProvider({required this.firebaseStorage});

  // Initialize recorder
  Future<bool> initRecorder() async {
    if (_isRecorderInitialized) return true;

    try {
      final status = await Permission.microphone.request();
      if (!status.isGranted) {
        print('❌ Microphone permission denied');
        return false;
      }

      await _recorder.openRecorder();
      _isRecorderInitialized = true;
      print('✅ Recorder initialized');
      return true;
    } catch (e) {
      print('❌ Error initializing recorder: $e');
      return false;
    }
  }

  // Initialize player
  Future<bool> initPlayer() async {
    if (_isPlayerInitialized) return true;

    try {
      await _player.openPlayer();
      _isPlayerInitialized = true;
      print('✅ Player initialized');
      return true;
    } catch (e) {
      print('❌ Error initializing player: $e');
      return false;
    }
  }

  // Start recording
  Future<bool> startRecording() async {
    try {
      if (!_isRecorderInitialized) {
        final initialized = await initRecorder();
        if (!initialized) return false;
      }

      final directory = await getTemporaryDirectory();
      final fileName = 'voice_${DateTime.now().millisecondsSinceEpoch}.aac';
      _currentRecordingPath = '${directory.path}/$fileName';

      await _recorder.startRecorder(
        toFile: _currentRecordingPath,
        codec: Codec.aacADTS,
      );

      print('🎤 Recording started: $_currentRecordingPath');
      return true;
    } catch (e) {
      print('❌ Error starting recording: $e');
      return false;
    }
  }

  // Stop recording
  Future<String?> stopRecording() async {
    try {
      await _recorder.stopRecorder();
      final path = _currentRecordingPath;
      _currentRecordingPath = null;

      print('🎤 Recording stopped: $path');
      return path;
    } catch (e) {
      print('❌ Error stopping recording: $e');
      return null;
    }
  }

  // Cancel recording
  Future<void> cancelRecording() async {
    try {
      await _recorder.stopRecorder();
      if (_currentRecordingPath != null) {
        final file = File(_currentRecordingPath!);
        if (await file.exists()) {
          await file.delete();
        }
      }
      _currentRecordingPath = null;
      print('🎤 Recording cancelled');
    } catch (e) {
      print('❌ Error cancelling recording: $e');
    }
  }

  // Get recording duration stream
  Stream<RecordingDisposition>? get recordingStream => _recorder.onProgress;

  // Upload voice message
  Future<String?> uploadVoiceMessage(String filePath, String fileName) async {
    try {
      final file = File(filePath);
      if (!await file.exists()) {
        print('❌ File does not exist: $filePath');
        return null;
      }

      final reference = firebaseStorage.ref().child('voice_messages/$fileName');
      final uploadTask = reference.putFile(file);
      final snapshot = await uploadTask;
      final url = await snapshot.ref.getDownloadURL();

      // Delete temporary file
      await file.delete();

      print('✅ Voice message uploaded: $url');
      return url;
    } catch (e) {
      print('❌ Error uploading voice message: $e');
      return null;
    }
  }

  // Play voice message
  Future<void> playVoiceMessage(String url) async {
    try {
      if (!_isPlayerInitialized) {
        final initialized = await initPlayer();
        if (!initialized) return;
      }

      await _player.startPlayer(
        fromURI: url,
        codec: Codec.aacADTS,
      );

      _player.setSubscriptionDuration(const Duration(milliseconds: 100));

      print('🔊 Playing voice message');
    } catch (e) {
      print('❌ Error playing voice message: $e');
    }
  }

  // Stop playback
  Future<void> stopPlayback() async {
    try {
      await _player.stopPlayer();
      print('🔊 Playback stopped');
    } catch (e) {
      print('❌ Error stopping playback: $e');
    }
  }

  // Pause playback
  Future<void> pausePlayback() async {
    try {
      await _player.pausePlayer();
      print('🔊 Playback paused');
    } catch (e) {
      print('❌ Error pausing playback: $e');
    }
  }

  // Resume playback
  Future<void> resumePlayback() async {
    try {
      await _player.resumePlayer();
      print('🔊 Playback resumed');
    } catch (e) {
      print('❌ Error resuming playback: $e');
    }
  }

  // Get player position stream
  Stream<PlaybackDisposition>? get playbackStream => _player.onProgress;

  // Check if recording
  bool get isRecording => _recorder.isRecording;

  // Check if playing
  bool get isPlaying => _player.isPlaying;

  // Dispose
  Future<void> dispose() async {
    try {
      if (_isRecorderInitialized) {
        await _recorder.closeRecorder();
        _isRecorderInitialized = false;
      }
      if (_isPlayerInitialized) {
        await _player.closePlayer();
        _isPlayerInitialized = false;
      }
      print('✅ Voice provider disposed');
    } catch (e) {
      print('❌ Error disposing voice provider: $e');
    }
  }
}
