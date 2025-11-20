// lib/widgets/bubble_manager.dart
import 'package:flutter/material.dart';
import 'package:flutter_chat_demo/pages/pages.dart';
import 'package:flutter_chat_demo/services/chat_bubble_service.dart';
import 'package:flutter_chat_demo/widgets/bubble_mini_chat.dart';

class BubbleManager extends StatefulWidget {
  final Widget child;

  const BubbleManager({super.key, required this.child});

  @override
  State<BubbleManager> createState() => _BubbleManagerState();
}

class _BubbleManagerState extends State<BubbleManager> {
  final _bubbleService = ChatBubbleService();
  final Map<String, OverlayEntry> _overlayEntries = {};

  @override
  void initState() {
    super.initState();
    _bubbleService.activeBubblesStream.listen(_handleBubbleChanges);
  }

  void _handleBubbleChanges(Map<String, BubbleData> bubbles) {
    // Remove inactive bubbles
    _overlayEntries.forEach((userId, entry) {
      if (!bubbles.containsKey(userId)) {
        entry.remove();
        _overlayEntries.remove(userId);
      }
    });

    // Add new bubbles
    bubbles.forEach((userId, data) {
      if (!_overlayEntries.containsKey(userId)) {
        _showMiniChat(data);
      }
    });
  }

  void _showMiniChat(BubbleData data) {
    final overlayEntry = OverlayEntry(
      builder: (context) => Positioned(
        right: 20,
        bottom: 100,
        child: BubbleMiniChat(
          peerId: data.userId,
          peerName: data.userName,
          peerAvatar: data.avatarUrl,
          onMaximize: () {
            _overlayEntries[data.userId]?.remove();
            _overlayEntries.remove(data.userId);
            _bubbleService.hideChatBubble(data.userId);

            Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => ChatPage(
                  arguments: ChatPageArguments(
                    peerId: data.userId,
                    peerAvatar: data.avatarUrl,
                    peerNickname: data.userName,
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );

    Overlay.of(context).insert(overlayEntry);
    _overlayEntries[data.userId] = overlayEntry;
  }

  @override
  Widget build(BuildContext context) {
    return widget.child;
  }

  @override
  void dispose() {
    _overlayEntries.forEach((_, entry) => entry.remove());
    _overlayEntries.clear();
    super.dispose();
  }
}
