// lib/widgets/mini_chat_overlay.dart
import 'package:flutter/material.dart';
import 'package:flutter_chat_demo/pages/chat_page.dart';

/// ✅ Mini Chat: Render ChatPage trong overlay nhỏ
class MiniChatOverlay extends StatelessWidget {
  final String peerId;
  final String peerNickname;
  final String peerAvatar;
  final VoidCallback? onMinimize;
  final VoidCallback? onClose;

  const MiniChatOverlay({
    super.key,
    required this.peerId,
    required this.peerNickname,
    required this.peerAvatar,
    this.onMinimize,
    this.onClose,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 350,
      height: 550,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.3),
            blurRadius: 20,
            offset: Offset(0, 10),
          ),
        ],
      ),
      child: Column(
        children: [
          // ✅ Custom header với minimize/close
          _buildHeader(context),

          // ✅ RENDER CHATPAGE (reuse toàn bộ logic)
          Expanded(
            child: ClipRRect(
              borderRadius: BorderRadius.vertical(
                bottom: Radius.circular(16),
              ),
              child: ChatPage(
                arguments: ChatPageArguments(
                  peerId: peerId,
                  peerAvatar: peerAvatar,
                  peerNickname: peerNickname,
                ),
                isMiniChat: true, // ✅ Flag để ChatPage biết đang ở mini mode
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Container(
      padding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: Color(0xff2196f3),
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: Row(
        children: [
          CircleAvatar(
            radius: 16,
            backgroundImage:
                peerAvatar.isNotEmpty ? NetworkImage(peerAvatar) : null,
            child: peerAvatar.isEmpty ? Icon(Icons.person, size: 16) : null,
          ),
          SizedBox(width: 8),
          Expanded(
            child: Text(
              peerNickname,
              style: TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.bold,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          IconButton(
            icon: Icon(Icons.remove, color: Colors.white, size: 20),
            onPressed: onMinimize,
            padding: EdgeInsets.zero,
            constraints: BoxConstraints(minWidth: 32, minHeight: 32),
          ),
          IconButton(
            icon: Icon(Icons.close, color: Colors.white, size: 20),
            onPressed: onClose,
            padding: EdgeInsets.zero,
            constraints: BoxConstraints(minWidth: 32, minHeight: 32),
          ),
        ],
      ),
    );
  }
}
