// lib/widgets/bubble_mini_chat.dart
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:flutter_chat_demo/constants/constants.dart';
import 'package:flutter_chat_demo/models/models.dart';
import 'package:flutter_chat_demo/providers/providers.dart';
import 'package:provider/provider.dart';

class BubbleMiniChat extends StatefulWidget {
  final String peerId;
  final String peerName;
  final String peerAvatar;
  final VoidCallback onMaximize;

  const BubbleMiniChat({
    super.key,
    required this.peerId,
    required this.peerName,
    required this.peerAvatar,
    required this.onMaximize,
  });

  @override
  State<BubbleMiniChat> createState() => _BubbleMiniChatState();
}

class _BubbleMiniChatState extends State<BubbleMiniChat> {
  final _messageController = TextEditingController();
  final _scrollController = ScrollController();
  late String _currentUserId;
  late String _groupChatId;
  late ChatProvider _chatProvider;
  late AuthProvider _authProvider;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _initializeProviders();
    });
  }

  void _initializeProviders() {
    _authProvider = context.read<AuthProvider>();
    _chatProvider = context.read<ChatProvider>();
    _currentUserId = _authProvider.userFirebaseId ?? '';

    if (_currentUserId.compareTo(widget.peerId) > 0) {
      _groupChatId = '$_currentUserId-${widget.peerId}';
    } else {
      _groupChatId = '${widget.peerId}-$_currentUserId';
    }

    setState(() {});
  }

  void _sendMessage() {
    final content = _messageController.text.trim();
    if (content.isEmpty) return;

    _chatProvider.sendMessage(
      content,
      0, // Text message
      _groupChatId,
      _currentUserId,
      widget.peerId,
    );

    _messageController.clear();

    // Scroll to bottom
    if (_scrollController.hasClients) {
      _scrollController.animateTo(
        0,
        duration: Duration(milliseconds: 300),
        curve: Curves.easeOut,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_currentUserId.isEmpty) {
      return Center(child: CircularProgressIndicator());
    }

    return Container(
      width: 300,
      height: 400,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.2),
            blurRadius: 10,
            offset: Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        children: [
          // Header
          Container(
            padding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: ColorConstants.primaryColor,
              borderRadius: BorderRadius.vertical(top: Radius.circular(12)),
            ),
            child: Row(
              children: [
                CircleAvatar(
                  radius: 16,
                  backgroundImage: widget.peerAvatar.isNotEmpty
                      ? NetworkImage(widget.peerAvatar)
                      : null,
                  child: widget.peerAvatar.isEmpty
                      ? Icon(Icons.person, size: 16)
                      : null,
                ),
                SizedBox(width: 8),
                Expanded(
                  child: Text(
                    widget.peerName,
                    style: TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                      fontSize: 14,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                IconButton(
                  icon: Icon(Icons.open_in_full, color: Colors.white, size: 18),
                  onPressed: widget.onMaximize,
                  padding: EdgeInsets.zero,
                  constraints: BoxConstraints(),
                ),
              ],
            ),
          ),

          // Messages
          Expanded(
            child: StreamBuilder<QuerySnapshot>(
              stream: _chatProvider.getChatStream(_groupChatId, 20),
              builder: (context, snapshot) {
                if (!snapshot.hasData) {
                  return Center(child: CircularProgressIndicator());
                }

                final messages = snapshot.data!.docs;

                return ListView.builder(
                  controller: _scrollController,
                  reverse: true,
                  padding: EdgeInsets.all(8),
                  itemCount: messages.length,
                  itemBuilder: (context, index) {
                    final message = MessageChat.fromDocument(messages[index]);
                    final isMyMessage = message.idFrom == _currentUserId;

                    return Align(
                      alignment: isMyMessage
                          ? Alignment.centerRight
                          : Alignment.centerLeft,
                      child: Container(
                        margin: EdgeInsets.symmetric(vertical: 2),
                        padding:
                            EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                        constraints: BoxConstraints(maxWidth: 200),
                        decoration: BoxDecoration(
                          color: isMyMessage
                              ? ColorConstants.primaryColor
                              : ColorConstants.greyColor2,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          message.content,
                          style: TextStyle(
                            color: isMyMessage ? Colors.white : Colors.black87,
                            fontSize: 13,
                          ),
                        ),
                      ),
                    );
                  },
                );
              },
            ),
          ),

          // Input
          Container(
            padding: EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: Colors.white,
              border: Border(
                top: BorderSide(color: ColorConstants.greyColor2),
              ),
            ),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _messageController,
                    decoration: InputDecoration(
                      hintText: 'Type a message...',
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(20),
                        borderSide: BorderSide.none,
                      ),
                      filled: true,
                      fillColor: ColorConstants.greyColor2,
                      contentPadding: EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 8,
                      ),
                      isDense: true,
                    ),
                    style: TextStyle(fontSize: 13),
                    onSubmitted: (_) => _sendMessage(),
                  ),
                ),
                SizedBox(width: 8),
                IconButton(
                  icon: Icon(Icons.send, color: ColorConstants.primaryColor),
                  onPressed: _sendMessage,
                  padding: EdgeInsets.zero,
                  constraints: BoxConstraints(),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _messageController.dispose();
    _scrollController.dispose();
    super.dispose();
  }
}
