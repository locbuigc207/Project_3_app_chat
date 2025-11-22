// lib/widgets/bubble_manager.dart - COMPLETE FIXED
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_chat_demo/pages/pages.dart';
import 'package:flutter_chat_demo/services/chat_bubble_service.dart';
import 'package:provider/provider.dart';

class BubbleManager extends StatefulWidget {
  final Widget child;

  const BubbleManager({super.key, required this.child});

  @override
  State<BubbleManager> createState() => _BubbleManagerState();
}

class _BubbleManagerState extends State<BubbleManager> {
  ChatBubbleService? _bubbleService;
  StreamSubscription? _bubbleClickSubscription;

  @override
  void initState() {
    super.initState();
    _initializeBubbleService();
  }

  void _initializeBubbleService() {
    // Only initialize on Android
    if (!Platform.isAndroid) return;

    try {
      _bubbleService = context.read<ChatBubbleService>();
      _listenToBubbleClicks();
    } catch (e) {
      print('⚠️ BubbleManager: Service not available: $e');
    }
  }

  void _listenToBubbleClicks() {
    if (_bubbleService == null) return;

    _bubbleClickSubscription?.cancel();
    _bubbleClickSubscription = _bubbleService!.bubbleClickStream.listen(
      (event) {
        _handleBubbleClick(event);
      },
      onError: (error) {
        print('❌ Bubble click stream error: $error');
      },
    );
  }

  void _handleBubbleClick(BubbleClickEvent event) {
    // Hide the bubble
    _bubbleService?.hideChatBubble(event.userId);

    // Navigate to chat
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ChatPage(
          arguments: ChatPageArguments(
            peerId: event.userId,
            peerAvatar: event.avatarUrl,
            peerNickname: event.userName,
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return widget.child;
  }

  @override
  void dispose() {
    _bubbleClickSubscription?.cancel();
    super.dispose();
  }
}

// Alternative: In-app floating bubble widget (for iOS compatibility)
class InAppBubbleOverlay extends StatefulWidget {
  final Widget child;

  const InAppBubbleOverlay({super.key, required this.child});

  @override
  State<InAppBubbleOverlay> createState() => _InAppBubbleOverlayState();
}

class _InAppBubbleOverlayState extends State<InAppBubbleOverlay> {
  final List<_FloatingBubble> _bubbles = [];

  void addBubble({
    required String peerId,
    required String peerName,
    required String peerAvatar,
  }) {
    // Remove existing bubble for same user
    _bubbles.removeWhere((b) => b.peerId == peerId);

    setState(() {
      _bubbles.add(_FloatingBubble(
        peerId: peerId,
        peerName: peerName,
        peerAvatar: peerAvatar,
        position: Offset(20, 100 + (_bubbles.length * 70)),
      ));
    });
  }

  void removeBubble(String peerId) {
    setState(() {
      _bubbles.removeWhere((b) => b.peerId == peerId);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        widget.child,
        ..._bubbles.map((bubble) => _buildBubbleWidget(bubble)),
      ],
    );
  }

  Widget _buildBubbleWidget(_FloatingBubble bubble) {
    return Positioned(
      left: bubble.position.dx,
      top: bubble.position.dy,
      child: GestureDetector(
        onPanUpdate: (details) {
          setState(() {
            final index = _bubbles.indexWhere((b) => b.peerId == bubble.peerId);
            if (index != -1) {
              _bubbles[index] = bubble.copyWith(
                position: Offset(
                  bubble.position.dx + details.delta.dx,
                  bubble.position.dy + details.delta.dy,
                ),
              );
            }
          });
        },
        onTap: () {
          removeBubble(bubble.peerId);
          Navigator.of(context).push(
            MaterialPageRoute(
              builder: (_) => ChatPage(
                arguments: ChatPageArguments(
                  peerId: bubble.peerId,
                  peerAvatar: bubble.peerAvatar,
                  peerNickname: bubble.peerName,
                ),
              ),
            ),
          );
        },
        child: Container(
          width: 60,
          height: 60,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            boxShadow: [
              BoxShadow(
                color: Colors.black26,
                blurRadius: 8,
                offset: Offset(2, 2),
              ),
            ],
          ),
          child: Stack(
            children: [
              CircleAvatar(
                radius: 30,
                backgroundImage: bubble.peerAvatar.isNotEmpty
                    ? NetworkImage(bubble.peerAvatar)
                    : null,
                child: bubble.peerAvatar.isEmpty
                    ? Icon(Icons.person, size: 30)
                    : null,
              ),
              Positioned(
                right: 0,
                top: 0,
                child: GestureDetector(
                  onTap: () => removeBubble(bubble.peerId),
                  child: Container(
                    width: 20,
                    height: 20,
                    decoration: BoxDecoration(
                      color: Colors.red,
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      Icons.close,
                      size: 12,
                      color: Colors.white,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _FloatingBubble {
  final String peerId;
  final String peerName;
  final String peerAvatar;
  final Offset position;

  _FloatingBubble({
    required this.peerId,
    required this.peerName,
    required this.peerAvatar,
    required this.position,
  });

  _FloatingBubble copyWith({
    String? peerId,
    String? peerName,
    String? peerAvatar,
    Offset? position,
  }) {
    return _FloatingBubble(
      peerId: peerId ?? this.peerId,
      peerName: peerName ?? this.peerName,
      peerAvatar: peerAvatar ?? this.peerAvatar,
      position: position ?? this.position,
    );
  }
}
