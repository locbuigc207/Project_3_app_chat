// lib/services/chat_bubble_service.dart
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class ChatBubbleService {
  static const MethodChannel _channel = MethodChannel('chat_bubble_overlay');

  static final ChatBubbleService _instance = ChatBubbleService._internal();
  factory ChatBubbleService() => _instance;
  ChatBubbleService._internal();

  final _activeBubblesController = StreamController<Map<String, BubbleData>>.broadcast();
  Stream<Map<String, BubbleData>> get activeBubblesStream => _activeBubblesController.stream;

  final Map<String, BubbleData> _activeBubbles = {};

  // Request overlay permission
  Future<bool> requestOverlayPermission() async {
    try {
      final bool hasPermission = await _channel.invokeMethod('requestPermission');
      return hasPermission;
    } catch (e) {
      print('Error requesting overlay permission: $e');
      return false;
    }
  }

  // Check if has overlay permission
  Future<bool> hasOverlayPermission() async {
    try {
      final bool hasPermission = await _channel.invokeMethod('hasPermission');
      return hasPermission;
    } catch (e) {
      print('Error checking overlay permission: $e');
      return false;
    }
  }

  // Show chat bubble
  Future<bool> showChatBubble({
    required String userId,
    required String userName,
    required String avatarUrl,
    String? lastMessage,
  }) async {
    try {
      final hasPermission = await hasOverlayPermission();
      if (!hasPermission) {
        final granted = await requestOverlayPermission();
        if (!granted) return false;
      }

      final bubbleData = BubbleData(
        userId: userId,
        userName: userName,
        avatarUrl: avatarUrl,
        lastMessage: lastMessage,
        timestamp: DateTime.now(),
      );

      final bool success = await _channel.invokeMethod('showBubble', {
        'userId': userId,
        'userName': userName,
        'avatarUrl': avatarUrl,
        'lastMessage': lastMessage,
      });

      if (success) {
        _activeBubbles[userId] = bubbleData;
        _activeBubblesController.add(_activeBubbles);
      }

      return success;
    } catch (e) {
      print('Error showing chat bubble: $e');
      return false;
    }
  }

  // Hide chat bubble
  Future<bool> hideChatBubble(String userId) async {
    try {
      final bool success = await _channel.invokeMethod('hideBubble', {
        'userId': userId,
      });

      if (success) {
        _activeBubbles.remove(userId);
        _activeBubblesController.add(_activeBubbles);
      }

      return success;
    } catch (e) {
      print('Error hiding chat bubble: $e');
      return false;
    }
  }

  // Hide all bubbles
  Future<void> hideAllBubbles() async {
    try {
      await _channel.invokeMethod('hideAllBubbles');
      _activeBubbles.clear();
      _activeBubblesController.add(_activeBubbles);
    } catch (e) {
      print('Error hiding all bubbles: $e');
    }
  }

  // Check if bubble is active
  bool isBubbleActive(String userId) {
    return _activeBubbles.containsKey(userId);
  }

  // Get active bubbles
  Map<String, BubbleData> get activeBubbles => Map.unmodifiable(_activeBubbles);

  void dispose() {
    _activeBubblesController.close();
  }
}

class BubbleData {
  final String userId;
  final String userName;
  final String avatarUrl;
  final String? lastMessage;
  final DateTime timestamp;

  BubbleData({
    required this.userId,
    required this.userName,
    required this.avatarUrl,
    this.lastMessage,
    required this.timestamp,
  });
}

// lib/widgets/bubble_mini_chat.dart
import 'package:flutter/material.dart';
import 'package:flutter_chat_demo/constants/constants.dart';
import 'package:flutter_chat_demo/models/models.dart';
import 'package:flutter_chat_demo/providers/providers.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
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
                        padding: EdgeInsets.symmetric(horizontal: 10, vertical: 6),
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

// lib/widgets/bubble_manager.dart
import 'package:flutter/material.dart';
import 'package:flutter_chat_demo/services/chat_bubble_service.dart';
import 'package:flutter_chat_demo/widgets/bubble_mini_chat.dart';
import 'package:flutter_chat_demo/pages/pages.dart';

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