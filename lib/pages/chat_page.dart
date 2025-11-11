import 'dart:async';
import 'dart:io';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_chat_demo/constants/constants.dart';
import 'package:flutter_chat_demo/models/models.dart';
import 'package:flutter_chat_demo/pages/pages.dart';
import 'package:flutter_chat_demo/providers/providers.dart';
import 'package:flutter_chat_demo/utils/utilities.dart';
import 'package:flutter_chat_demo/widgets/widgets.dart';
import 'package:fluttertoast/fluttertoast.dart';
import 'package:image_picker/image_picker.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

class ChatPage extends StatefulWidget {
  const ChatPage({super.key, required this.arguments});

  final ChatPageArguments arguments;

  @override
  ChatPageState createState() => ChatPageState();
}

class ChatPageState extends State<ChatPage> {
  late final String _currentUserId;

  List<QueryDocumentSnapshot> _listMessage = [];
  int _limit = 20;
  final _limitIncrement = 20;
  String _groupChatId = "";

  File? _imageFile;
  bool _isLoading = false;
  bool _isShowSticker = false;
  String _imageUrl = "";

  final _chatInputController = TextEditingController();
  final _listScrollController = ScrollController();
  final _focusNode = FocusNode();

  late final _chatProvider = context.read<ChatProvider>();
  late final _authProvider = context.read<AuthProvider>();
  late final _messageProvider = context.read<MessageProvider>();
  late final _reactionProvider = context.read<ReactionProvider>();

  // Pinned messages
  List<DocumentSnapshot> _pinnedMessages = [];

  @override
  void initState() {
    super.initState();
    _focusNode.addListener(_onFocusChange);
    _listScrollController.addListener(_scrollListener);
    _readLocal();
    _loadPinnedMessages();
  }

  void _scrollListener() {
    if (!_listScrollController.hasClients) return;
    if (_listScrollController.offset >= _listScrollController.position.maxScrollExtent &&
        !_listScrollController.position.outOfRange &&
        _limit <= _listMessage.length) {
      setState(() {
        _limit += _limitIncrement;
      });
    }
  }

  void _onFocusChange() {
    if (_focusNode.hasFocus) {
      setState(() {
        _isShowSticker = false;
      });
    }
  }

  void _readLocal() {
    if (_authProvider.userFirebaseId?.isNotEmpty == true) {
      _currentUserId = _authProvider.userFirebaseId!;
    } else {
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => LoginPage()),
            (_) => false,
      );
    }
    String peerId = widget.arguments.peerId;
    if (_currentUserId.compareTo(peerId) > 0) {
      _groupChatId = '$_currentUserId-$peerId';
    } else {
      _groupChatId = '$peerId-$_currentUserId';
    }

    _chatProvider.updateDataFirestore(
      FirestoreConstants.pathUserCollection,
      _currentUserId,
      {FirestoreConstants.chattingWith: peerId},
    );
  }

  void _loadPinnedMessages() {
    _messageProvider.getPinnedMessages(_groupChatId).listen((snapshot) {
      setState(() {
        _pinnedMessages = snapshot.docs;
      });
    });
  }

  Future<bool> _pickImage() async {
    final imagePicker = ImagePicker();
    final pickedXFile = await imagePicker.pickImage(source: ImageSource.gallery).catchError((err) {
      Fluttertoast.showToast(msg: err.toString());
      return null;
    });
    if (pickedXFile != null) {
      final imageFile = File(pickedXFile.path);
      setState(() {
        _imageFile = imageFile;
        _isLoading = true;
      });
      return true;
    } else {
      return false;
    }
  }

  void _getSticker() {
    _focusNode.unfocus();
    setState(() {
      _isShowSticker = !_isShowSticker;
    });
  }

  Future<void> _uploadFile() async {
    final fileName = DateTime.now().millisecondsSinceEpoch.toString();
    final uploadTask = _chatProvider.uploadFile(_imageFile!, fileName);
    try {
      final snapshot = await uploadTask;
      _imageUrl = await snapshot.ref.getDownloadURL();
      setState(() {
        _isLoading = false;
        _onSendMessage(_imageUrl, TypeMessage.image);
      });
    } on FirebaseException catch (e) {
      setState(() {
        _isLoading = false;
      });
      Fluttertoast.showToast(msg: e.message ?? e.toString());
    }
  }

  void _onSendMessage(String content, int type) {
    if (content.trim().isNotEmpty) {
      _chatInputController.clear();
      _chatProvider.sendMessage(content, type, _groupChatId, _currentUserId, widget.arguments.peerId);
      if (_listScrollController.hasClients) {
        _listScrollController.animateTo(0, duration: Duration(milliseconds: 300), curve: Curves.easeOut);
      }
    } else {
      Fluttertoast.showToast(msg: 'Nothing to send', backgroundColor: ColorConstants.greyColor);
    }
  }

  void _showMessageOptions(DocumentSnapshot doc) {
    final message = MessageChat.fromDocument(doc);
    final isOwnMessage = message.idFrom == _currentUserId;

    showModalBottomSheet(
      context: context,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) => MessageOptionsDialog(
        isOwnMessage: isOwnMessage,
        isPinned: message.isPinned,
        isDeleted: message.isDeleted,
        onEdit: () => _editMessage(doc),
        onDelete: () => _deleteMessage(doc.id),
        onPin: () => _togglePinMessage(doc),
        onCopy: () => _copyMessage(message.content),
        onReply: () => _replyToMessage(message),
      ),
    );
  }

  void _editMessage(DocumentSnapshot doc) {
    final message = MessageChat.fromDocument(doc);

    showDialog(
      context: context,
      builder: (context) => EditMessageDialog(
        originalContent: message.content,
        onSave: (newContent) async {
          final success = await _messageProvider.editMessage(
            _groupChatId,
            doc.id,
            newContent,
          );
          if (success) {
            Fluttertoast.showToast(msg: 'Message edited');
          }
        },
      ),
    );
  }

  Future<void> _deleteMessage(String messageId) async {
    final success = await _messageProvider.deleteMessage(_groupChatId, messageId);
    if (success) {
      Fluttertoast.showToast(msg: 'Message deleted');
    }
  }

  Future<void> _togglePinMessage(DocumentSnapshot doc) async {
    final message = MessageChat.fromDocument(doc);
    final success = await _messageProvider.togglePinMessage(
      _groupChatId,
      doc.id,
      message.isPinned,
    );
    if (success) {
      Fluttertoast.showToast(msg: message.isPinned ? 'Message unpinned' : 'Message pinned');
    }
  }

  void _copyMessage(String content) {
    Clipboard.setData(ClipboardData(text: content));
    Fluttertoast.showToast(msg: 'Message copied');
  }

  void _replyToMessage(MessageChat message) {
    // TODO: Implement reply functionality
    Fluttertoast.showToast(msg: 'Reply feature coming soon');
  }

  void _showReactionPicker(DocumentSnapshot doc) {
    showModalBottomSheet(
      context: context,
      builder: (context) => ReactionPicker(
        onEmojiSelected: (emoji) {
          Navigator.pop(context);
          _reactionProvider.toggleReaction(
            _groupChatId,
            doc.id,
            _currentUserId,
            emoji,
          );
        },
      ),
    );
  }

  Widget _buildItemMessage(int index, DocumentSnapshot? document) {
    if (document == null) return SizedBox.shrink();
    final messageChat = MessageChat.fromDocument(document);

    // Show deleted message
    if (messageChat.isDeleted) {
      return Container(
        margin: EdgeInsets.only(bottom: 10, left: 10, right: 10),
        padding: EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: ColorConstants.greyColor2.withOpacity(0.5),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.block, size: 16, color: ColorConstants.greyColor),
            SizedBox(width: 8),
            Text(
              'This message was deleted',
              style: TextStyle(
                color: ColorConstants.greyColor,
                fontStyle: FontStyle.italic,
              ),
            ),
          ],
        ),
      );
    }

    final isMyMessage = messageChat.idFrom == _currentUserId;

    return GestureDetector(
      onLongPress: () => _showMessageOptions(document),
      child: Row(
        mainAxisAlignment: isMyMessage ? MainAxisAlignment.end : MainAxisAlignment.start,
        children: [
          if (!isMyMessage)
            ClipOval(
              child: _isLastMessageLeft(index)
                  ? Image.network(
                widget.arguments.peerAvatar,
                loadingBuilder: (_, child, loadingProgress) {
                  if (loadingProgress == null) return child;
                  return Center(
                    child: CircularProgressIndicator(
                      color: ColorConstants.themeColor,
                      value: loadingProgress.expectedTotalBytes != null
                          ? loadingProgress.cumulativeBytesLoaded / loadingProgress.expectedTotalBytes!
                          : null,
                    ),
                  );
                },
                errorBuilder: (_, __, ___) {
                  return Icon(
                    Icons.account_circle,
                    size: 35,
                    color: ColorConstants.greyColor,
                  );
                },
                width: 35,
                height: 35,
                fit: BoxFit.cover,
              )
                  : Container(width: 35),
            ),
          SizedBox(width: isMyMessage ? 0 : 10),
          Flexible(
            child: Column(
              crossAxisAlignment: isMyMessage ? CrossAxisAlignment.end : CrossAxisAlignment.start,
              children: [
                // Message content
                Container(
                  constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.7),
                  child: messageChat.type == TypeMessage.text
                      ? Container(
                    padding: EdgeInsets.fromLTRB(15, 10, 15, 10),
                    decoration: BoxDecoration(
                      color: isMyMessage ? ColorConstants.greyColor2 : ColorConstants.primaryColor,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          messageChat.content,
                          style: TextStyle(
                            color: isMyMessage ? ColorConstants.primaryColor : Colors.white,
                          ),
                        ),
                        if (messageChat.editedAt != null)
                          Padding(
                            padding: EdgeInsets.only(top: 4),
                            child: Text(
                              '(edited)',
                              style: TextStyle(
                                fontSize: 10,
                                color: isMyMessage
                                    ? ColorConstants.greyColor
                                    : Colors.white70,
                                fontStyle: FontStyle.italic,
                              ),
                            ),
                          ),
                      ],
                    ),
                  )
                      : messageChat.type == TypeMessage.image
                      ? Container(
                    clipBehavior: Clip.hardEdge,
                    decoration: BoxDecoration(borderRadius: BorderRadius.circular(8)),
                    child: GestureDetector(
                      child: Image.network(
                        messageChat.content,
                        loadingBuilder: (_, child, loadingProgress) {
                          if (loadingProgress == null) return child;
                          return Container(
                            decoration: BoxDecoration(
                              color: ColorConstants.greyColor2,
                              borderRadius: BorderRadius.all(Radius.circular(8)),
                            ),
                            width: 200,
                            height: 200,
                            child: Center(
                              child: CircularProgressIndicator(
                                color: ColorConstants.themeColor,
                                value: loadingProgress.expectedTotalBytes != null
                                    ? loadingProgress.cumulativeBytesLoaded /
                                    loadingProgress.expectedTotalBytes!
                                    : null,
                              ),
                            ),
                          );
                        },
                        errorBuilder: (_, __, ___) {
                          return Image.asset(
                            'images/img_not_available.jpeg',
                            width: 200,
                            height: 200,
                            fit: BoxFit.cover,
                          );
                        },
                        width: 200,
                        height: 200,
                        fit: BoxFit.cover,
                      ),
                      onTap: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => FullPhotoPage(url: messageChat.content),
                          ),
                        );
                      },
                    ),
                  )
                      : Container(
                    child: Image.asset(
                      'images/${messageChat.content}.gif',
                      width: 100,
                      height: 100,
                      fit: BoxFit.cover,
                    ),
                  ),
                ),

                // Reactions
                StreamBuilder<QuerySnapshot>(
                  stream: _reactionProvider.getReactions(_groupChatId, document.id),
                  builder: (context, snapshot) {
                    if (!snapshot.hasData || snapshot.data!.docs.isEmpty) {
                      return SizedBox.shrink();
                    }

                    Map<String, int> reactions = {};
                    for (var doc in snapshot.data!.docs) {
                      final emoji = doc.get('emoji') as String;
                      reactions[emoji] = (reactions[emoji] ?? 0) + 1;
                    }

                    Map<String, bool> userReactions = {};
                    for (var doc in snapshot.data!.docs) {
                      if (doc.get('userId') == _currentUserId) {
                        userReactions[doc.get('emoji')] = true;
                      }
                    }

                    return Padding(
                      padding: EdgeInsets.only(top: 4),
                      child: MessageReactionsDisplay(
                        reactions: reactions,
                        currentUserId: _currentUserId,
                        userReactions: userReactions,
                        onReactionTap: (emoji) {
                          _reactionProvider.toggleReaction(
                            _groupChatId,
                            document.id,
                            _currentUserId,
                            emoji,
                          );
                        },
                      ),
                    );
                  },
                ),

                // Add reaction button & timestamp
                if (_isLastMessageLeft(index) || _isLastMessageRight(index))
                  Padding(
                    padding: EdgeInsets.only(top: 5, left: isMyMessage ? 0 : 50, right: isMyMessage ? 10 : 0),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        InkWell(
                          onTap: () => _showReactionPicker(document),
                          child: Container(
                            padding: EdgeInsets.all(4),
                            child: Icon(
                              Icons.add_reaction_outlined,
                              size: 16,
                              color: ColorConstants.greyColor,
                            ),
                          ),
                        ),
                        SizedBox(width: 8),
                        Text(
                          DateFormat('dd MMM kk:mm').format(
                            DateTime.fromMillisecondsSinceEpoch(int.parse(messageChat.timestamp)),
                          ),
                          style: TextStyle(
                            color: ColorConstants.greyColor,
                            fontSize: 12,
                            fontStyle: FontStyle.italic,
                          ),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  bool _isLastMessageLeft(int index) {
    if ((index > 0 && _listMessage[index - 1].get(FirestoreConstants.idFrom) == _currentUserId) || index == 0) {
      return true;
    } else {
      return false;
    }
  }

  bool _isLastMessageRight(int index) {
    if ((index > 0 && _listMessage[index - 1].get(FirestoreConstants.idFrom) != _currentUserId) || index == 0) {
      return true;
    } else {
      return false;
    }
  }

  void _onBackPress() {
    _chatProvider.updateDataFirestore(
      FirestoreConstants.pathUserCollection,
      _currentUserId,
      {FirestoreConstants.chattingWith: null},
    );
    Navigator.pop(context);
  }

  Widget _buildPinnedMessages() {
    if (_pinnedMessages.isEmpty) return SizedBox.shrink();

    return Container(
      padding: EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: ColorConstants.primaryColor.withOpacity(0.1),
        border: Border(
          bottom: BorderSide(color: ColorConstants.greyColor2),
        ),
      ),
      child: Row(
        children: [
          Icon(Icons.push_pin, size: 16, color: ColorConstants.primaryColor),
          SizedBox(width: 8),
          Expanded(
            child: Text(
              'Pinned: ${MessageChat.fromDocument(_pinnedMessages.first).content}',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(fontSize: 12),
            ),
          ),
          if (_pinnedMessages.length > 1)
            Text(
              '+${_pinnedMessages.length - 1}',
              style: TextStyle(
                fontSize: 12,
                color: ColorConstants.primaryColor,
              ),
            ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(
          widget.arguments.peerNickname,
          style: TextStyle(color: ColorConstants.primaryColor),
        ),
        centerTitle: true,
        actions: [
          IconButton(
            icon: Icon(Icons.search),
            onPressed: () async {
              final result = await Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) => SearchMessagesPage(
                    groupChatId: _groupChatId,
                    peerName: widget.arguments.peerNickname,
                  ),
                ),
              );
              // TODO: Scroll to message if result is returned
            },
          ),
        ],
      ),
      body: SafeArea(
        child: PopScope(
          child: Stack(
            children: [
              Column(
                children: [
                  _buildPinnedMessages(),
                  _buildListMessage(),
                  _isShowSticker ? _buildStickers() : SizedBox.shrink(),
                  _buildInput(),
                ],
              ),
              Positioned(
                child: _isLoading ? LoadingView() : SizedBox.shrink(),
              ),
            ],
          ),
          canPop: false,
          onPopInvokedWithResult: (didPop, result) {
            if (didPop) return;
            _onBackPress();
          },
        ),
      ),
    );
  }

  Widget _buildStickers() {
    return Container(
      child: Column(
        children: [
          Row(
            children: [
              _buildItemSticker("mimi1"),
              _buildItemSticker("mimi2"),
              _buildItemSticker("mimi3"),
            ],
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          ),
          Row(
            children: [
              _buildItemSticker("mimi4"),
              _buildItemSticker("mimi5"),
              _buildItemSticker("mimi6"),
            ],
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          ),
          Row(
            children: [
              _buildItemSticker("mimi7"),
              _buildItemSticker("mimi8"),
              _buildItemSticker("mimi9"),
            ],
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          )
        ],
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      ),
      decoration: BoxDecoration(
        border: Border(top: BorderSide(color: ColorConstants.greyColor2, width: 0.5)),
        color: Colors.white,
      ),
      padding: EdgeInsets.symmetric(vertical: 8),
    );
  }

  Widget _buildItemSticker(String stickerName) {
    return TextButton(
      onPressed: () => _onSendMessage(stickerName, TypeMessage.sticker),
      child: Image.asset(
        'images/$stickerName.gif',
        width: 50,
        height: 50,
        fit: BoxFit.cover,
      ),
    );
  }

  Widget _buildInput() {
    return Container(
      child: Row(
        children: [
          Material(
            child: Container(
              margin: EdgeInsets.symmetric(horizontal: 1),
              child: IconButton(
                icon: Icon(Icons.image),
                onPressed: () {
                  _pickImage().then((isSuccess) {
                    if (isSuccess) _uploadFile();
                  });
                },
                color: ColorConstants.primaryColor,
              ),
            ),
            color: Colors.white,
          ),
          Material(
            child: Container(
              margin: EdgeInsets.symmetric(horizontal: 1),
              child: IconButton(
                icon: Icon(Icons.face),
                onPressed: _getSticker,
                color: ColorConstants.primaryColor,
              ),
            ),
            color: Colors.white,
          ),
          Flexible(
            child: Container(
              child: TextField(
                onTapOutside: (_) {
                  Utilities.closeKeyboard();
                },
                onSubmitted: (_) {
                  _onSendMessage(_chatInputController.text, TypeMessage.text);
                },
                style: TextStyle(color: ColorConstants.primaryColor, fontSize: 15),
                controller: _chatInputController,
                decoration: InputDecoration.collapsed(
                  hintText: 'Type your message...',
                  hintStyle: TextStyle(color: ColorConstants.greyColor),
                ),
                focusNode: _focusNode,
              ),
            ),
          ),
          Material(
            child: Container(
              margin: EdgeInsets.symmetric(horizontal: 8),
              child: IconButton(
                icon: Icon(Icons.send),
                onPressed: () => _onSendMessage(_chatInputController.text, TypeMessage.text),
                color: ColorConstants.primaryColor,
              ),
            ),
            color: Colors.white,
          ),
        ],
      ),
      width: double.infinity,
      height: 50,
      decoration: BoxDecoration(
          border: Border(top: BorderSide(color: ColorConstants.greyColor2, width: 0.5)), color: Colors.white),
    );
  }

  Widget _buildListMessage() {
    return Flexible(
      child: _groupChatId.isNotEmpty
          ? StreamBuilder<QuerySnapshot>(
        stream: _chatProvider.getChatStream(_groupChatId, _limit),
        builder: (_, snapshot) {
          if (snapshot.hasData) {
            _listMessage = snapshot.data!.docs;
            if (_listMessage.length > 0) {
              return ListView.builder(
                padding: EdgeInsets.all(10),
                itemBuilder: (_, index) => _buildItemMessage(index, snapshot.data?.docs[index]),
                itemCount: snapshot.data?.docs.length,
                reverse: true,
                controller: _listScrollController,
              );
            } else {
              return Center(child: Text("No message here yet..."));
            }
          } else {
            return Center(
              child: CircularProgressIndicator(
                color: ColorConstants.themeColor,
              ),
            );
          }
        },
      )
          : Center(
        child: CircularProgressIndicator(
          color: ColorConstants.themeColor,
        ),
      ),
    );
  }

  @override
  void dispose() {
    _chatInputController.dispose();
    _listScrollController
      ..removeListener(_scrollListener)
      ..dispose();
    _focusNode.dispose();
    super.dispose();
  }
}

class ChatPageArguments {
  final String peerId;
  final String peerAvatar;
  final String peerNickname;

  ChatPageArguments({required this.peerId, required this.peerAvatar, required this.peerNickname});
}