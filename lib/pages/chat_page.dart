// lib/pages/chat_page.dart (COMPLETE - ALL METHODS IMPLEMENTED)
import 'dart:async';
import 'dart:io';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_chat_demo/constants/constants.dart';
import 'package:flutter_chat_demo/models/models.dart';
import 'package:flutter_chat_demo/pages/pages.dart';
import 'package:flutter_chat_demo/providers/providers.dart';
import 'package:flutter_chat_demo/utils/utils.dart';
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
  UserPresenceProvider? _presenceProvider;

  Timer? _typingTimer;
  bool _isTyping = false;

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

  late ChatProvider _chatProvider;
  late AuthProvider _authProvider;
  late MessageProvider _messageProvider;
  late ReactionProvider _reactionProvider;
  late ReminderProvider _reminderProvider;
  late AutoDeleteProvider _autoDeleteProvider;
  late ConversationLockProvider _lockProvider;
  late ViewOnceProvider _viewOnceProvider;
  late SmartReplyProvider _smartReplyProvider;

  List<DocumentSnapshot> _pinnedMessages = [];
  StreamSubscription<QuerySnapshot>? _pinnedSub;

  List<SmartReply> _smartReplies = [];
  String _lastReceivedMessage = '';

  MessageChat? _replyingTo;
  bool _conversationLockedChecked = false;

  StreamSubscription<QuerySnapshot>? _unreadMessagesSubscription;

  @override
  void initState() {
    super.initState();
    _focusNode.addListener(_onFocusChange);
    _listScrollController.addListener(_scrollListener);

    WidgetsBinding.instance.addPostFrameCallback((_) {
      _initializeProviders(context);
    });
  }

  void _initializeProviders(BuildContext context) {
    _chatProvider = context.read<ChatProvider>();
    _authProvider = context.read<AuthProvider>();
    _messageProvider = context.read<MessageProvider>();
    _reactionProvider = context.read<ReactionProvider>();
    _reminderProvider = context.read<ReminderProvider>();
    _autoDeleteProvider = context.read<AutoDeleteProvider>();
    _lockProvider = context.read<ConversationLockProvider>();
    _viewOnceProvider = context.read<ViewOnceProvider>();
    _smartReplyProvider = context.read<SmartReplyProvider>();
    _presenceProvider = context.read<UserPresenceProvider>();

    _readLocal();
    _loadPinnedMessages();
    _checkConversationLock();
    _loadSmartReplies();
    _setupAutoReadMarking();

    if (_presenceProvider != null) {
      _presenceProvider!.setUserOnline(_currentUserId);
      _presenceProvider!.markMessagesAsRead(
        conversationId: _groupChatId,
        userId: _currentUserId,
      );
    }

    ErrorLogger.logScreenView('chat_page');
  }

  void _scrollListener() {
    if (!_listScrollController.hasClients) return;
    final pos = _listScrollController.position;
    if (pos.pixels >= pos.maxScrollExtent - 100 &&
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
      return;
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

    Future.delayed(Duration(milliseconds: 500), () {
      _markMessagesAsRead();
    });
  }

  void _loadPinnedMessages() {
    _pinnedSub?.cancel();
    _pinnedSub = _messageProvider.getPinnedMessages(_groupChatId).listen(
      (snapshot) {
        if (!mounted) return;
        setState(() {
          _pinnedMessages = snapshot.docs;
        });
      },
      onError: (err) {
        ErrorLogger.logError(err, null, context: 'Load Pinned Messages');
      },
    );
  }

  Future<bool> _pickImage() async {
    try {
      final imagePicker = ImagePicker();
      final pickedXFile = await imagePicker.pickImage(
        source: ImageSource.gallery,
      );

      if (pickedXFile != null) {
        final imageFile = File(pickedXFile.path);
        if (!mounted) return false;
        setState(() {
          _imageFile = imageFile;
          _isLoading = true;
        });
        return true;
      }
      return false;
    } catch (e) {
      ErrorLogger.logError(e, null, context: 'Pick Image');
      Fluttertoast.showToast(msg: 'Failed to pick image');
      return false;
    }
  }

  void _getSticker() {
    _focusNode.unfocus();
    setState(() {
      _isShowSticker = !_isShowSticker;
    });
  }

  void _handleTyping(String text) {
    if (_presenceProvider == null) return;

    if (text.isEmpty) {
      if (_isTyping) {
        _isTyping = false;
        _presenceProvider!.setTypingStatus(
          conversationId: _groupChatId,
          userId: _currentUserId,
          isTyping: false,
        );
      }
      return;
    }

    if (!_isTyping) {
      _isTyping = true;
      _presenceProvider!.setTypingStatus(
        conversationId: _groupChatId,
        userId: _currentUserId,
        isTyping: true,
      );
    }

    _typingTimer?.cancel();
    _typingTimer = Timer(const Duration(seconds: 3), () {
      _isTyping = false;
      _presenceProvider?.setTypingStatus(
        conversationId: _groupChatId,
        userId: _currentUserId,
        isTyping: false,
      );
    });
  }

  Widget _buildTypingIndicator() {
    if (_presenceProvider == null) return const SizedBox.shrink();

    return StreamBuilder<Map<String, bool>>(
      stream: _presenceProvider!.getTypingStatus(_groupChatId),
      builder: (context, snapshot) {
        if (!snapshot.hasData) return const SizedBox.shrink();

        final typingUsers = snapshot.data!;
        final peerTyping = typingUsers[widget.arguments.peerId] ?? false;

        if (!peerTyping) return const SizedBox.shrink();

        return TypingIndicator(userName: widget.arguments.peerNickname);
      },
    );
  }

  void _setupAutoReadMarking() {
    _unreadMessagesSubscription = FirebaseFirestore.instance
        .collection(FirestoreConstants.pathMessageCollection)
        .doc(_groupChatId)
        .collection(_groupChatId)
        .where(FirestoreConstants.idTo, isEqualTo: _currentUserId)
        .where('isRead', isEqualTo: false)
        .snapshots()
        .listen((snapshot) {
      if (snapshot.docs.isNotEmpty) {
        _markMessagesAsRead();
      }
    }, onError: (error) {
      ErrorLogger.logError(error, null, context: 'Setup Auto Read');
    });
  }

  Future<void> _uploadFile() async {
    if (_imageFile == null) return;

    try {
      final fileName = DateTime.now().millisecondsSinceEpoch.toString();
      final uploadTask = _chatProvider.uploadFile(_imageFile!, fileName);
      final snapshot = await uploadTask;
      _imageUrl = await snapshot.ref.getDownloadURL();

      if (!mounted) return;
      setState(() {
        _isLoading = false;
      });

      await _onSendMessageWithAutoDelete(_imageUrl, TypeMessage.image);
    } catch (e) {
      ErrorLogger.logError(e, null, context: 'Upload File');

      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
      Fluttertoast.showToast(msg: 'Upload failed');
    }
  }

  Future<void> _onSendMessageWithAutoDelete(String content, int type) async {
    if (content.trim().isEmpty) {
      Fluttertoast.showToast(
        msg: 'Nothing to send',
        backgroundColor: ColorConstants.greyColor,
      );
      return;
    }

    String finalContent = content;
    if (_replyingTo != null) {
      finalContent = '↪ ${_replyingTo!.content}\n$finalContent';
    }

    _chatInputController.clear();
    setState(() {
      _replyingTo = null;
      _smartReplies = [];
    });

    try {
      _chatProvider.sendMessage(
        finalContent,
        type,
        _groupChatId,
        _currentUserId,
        widget.arguments.peerId,
      );

      ErrorLogger.logMessageSent(
        conversationId: _groupChatId,
        messageType: type,
      );
    } catch (e) {
      ErrorLogger.logError(e, null, context: 'Send Message');
      Fluttertoast.showToast(msg: 'Send failed');
      return;
    }

    try {
      final messageId = DateTime.now().millisecondsSinceEpoch.toString();
      await _autoDeleteProvider.scheduleMessageDeletion(
        groupChatId: _groupChatId,
        messageId: messageId,
        conversationId: _groupChatId,
      );
    } catch (e) {
      ErrorLogger.logError(e, null, context: 'Schedule Auto Delete');
    }

    await _loadSmartReplies();

    if (_listScrollController.hasClients) {
      _listScrollController.animateTo(
        0,
        duration: Duration(milliseconds: 300),
        curve: Curves.easeOut,
      );
    }
  }

  Future<void> _markMessagesAsRead() async {
    try {
      final unreadMessages = await FirebaseFirestore.instance
          .collection(FirestoreConstants.pathMessageCollection)
          .doc(_groupChatId)
          .collection(_groupChatId)
          .where(FirestoreConstants.idTo, isEqualTo: _currentUserId)
          .where('isRead', isEqualTo: false)
          .get();

      if (unreadMessages.docs.isEmpty) return;

      final batch = FirebaseFirestore.instance.batch();

      for (var doc in unreadMessages.docs) {
        batch.update(doc.reference, {
          'isRead': true,
          'readAt': FieldValue.serverTimestamp(),
        });
      }

      await batch.commit();

      ErrorLogger.logMessageRead(conversationId: _groupChatId);

      print('✅ Marked ${unreadMessages.docs.length} messages as read');
    } catch (e) {
      ErrorLogger.logError(e, null, context: 'Mark Messages Read');
    }
  }

  // ✅ MISSING METHOD 1: Show Advanced Message Options
  void _showAdvancedMessageOptions(MessageChat message, String messageId) {
    showModalBottomSheet(
      context: context,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) => MessageOptionsDialog(
        isOwnMessage: message.idFrom == _currentUserId,
        isPinned: message.isPinned,
        isDeleted: message.isDeleted,
        onEdit: () => _editMessage(messageId, message.content),
        onDelete: () => _deleteMessage(messageId),
        onPin: () => _togglePinMessage(messageId, message.isPinned),
        onCopy: () => _copyMessage(message.content),
        onReply: () => _setReplyToMessage(message),
      ),
    );
  }

  // ✅ MISSING METHOD 2: Edit Message
  Future<void> _editMessage(String messageId, String currentContent) async {
    showDialog(
      context: context,
      builder: (context) => EditMessageDialog(
        originalContent: currentContent,
        onSave: (newContent) async {
          final success = await _messageProvider.editMessage(
            _groupChatId,
            messageId,
            newContent,
          );
          if (success) {
            Fluttertoast.showToast(msg: 'Message edited');
          }
        },
      ),
    );
  }

  // ✅ MISSING METHOD 3: Delete Message
  Future<void> _deleteMessage(String messageId) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Delete Message'),
        content: Text('Are you sure you want to delete this message?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text('Delete', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );

    if (confirm == true) {
      final success = await _messageProvider.deleteMessage(
        _groupChatId,
        messageId,
      );
      if (success) {
        Fluttertoast.showToast(msg: 'Message deleted');
      }
    }
  }

  // ✅ MISSING METHOD 4: Toggle Pin Message
  Future<void> _togglePinMessage(String messageId, bool currentStatus) async {
    final success = await _messageProvider.togglePinMessage(
      _groupChatId,
      messageId,
      currentStatus,
    );
    if (success) {
      Fluttertoast.showToast(
        msg: currentStatus ? 'Message unpinned' : 'Message pinned',
      );
    }
  }

  // ✅ MISSING METHOD 5: Copy Message
  void _copyMessage(String content) {
    Clipboard.setData(ClipboardData(text: content));
    Fluttertoast.showToast(msg: 'Copied to clipboard');
  }

  // ✅ MISSING METHOD 6: Set Reply To Message
  void _setReplyToMessage(MessageChat message) {
    setState(() {
      _replyingTo = message;
    });
    _focusNode.requestFocus();
  }

  // ✅ MISSING METHOD 7: Show Reaction Picker
  void _showReactionPicker(String messageId) {
    showDialog(
      context: context,
      builder: (context) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        child: ReactionPicker(
          onEmojiSelected: (emoji) {
            _reactionProvider.toggleReaction(
              _groupChatId,
              messageId,
              _currentUserId,
              emoji,
            );
            Navigator.pop(context);
          },
        ),
      ),
    );
  }

  // ✅ MISSING METHOD 8: Pick Time with Wheel
  Future<DateTime?> _pickTimeWithWheel() async {
    DateTime selectedTime = DateTime.now().add(Duration(hours: 1));

    final result = await showDialog<DateTime>(
      context: context,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setState) {
            return AlertDialog(
              title: Text('Set Reminder Time'),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Date Picker
                  ListTile(
                    title: Text('Date'),
                    subtitle:
                        Text(DateFormat('MMM dd, yyyy').format(selectedTime)),
                    trailing: Icon(Icons.calendar_today),
                    onTap: () async {
                      final date = await showDatePicker(
                        context: context,
                        initialDate: selectedTime,
                        firstDate: DateTime.now(),
                        lastDate: DateTime.now().add(Duration(days: 365)),
                      );
                      if (date != null) {
                        setState(() {
                          selectedTime = DateTime(
                            date.year,
                            date.month,
                            date.day,
                            selectedTime.hour,
                            selectedTime.minute,
                          );
                        });
                      }
                    },
                  ),
                  // Time Picker
                  ListTile(
                    title: Text('Time'),
                    subtitle: Text(DateFormat('HH:mm').format(selectedTime)),
                    trailing: Icon(Icons.access_time),
                    onTap: () async {
                      final time = await showTimePicker(
                        context: context,
                        initialTime: TimeOfDay.fromDateTime(selectedTime),
                      );
                      if (time != null) {
                        setState(() {
                          selectedTime = DateTime(
                            selectedTime.year,
                            selectedTime.month,
                            selectedTime.day,
                            time.hour,
                            time.minute,
                          );
                        });
                      }
                    },
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: Text('Cancel'),
                ),
                TextButton(
                  onPressed: () => Navigator.pop(context, selectedTime),
                  child: Text('Set'),
                ),
              ],
            );
          },
        );
      },
    );

    return result;
  }

  // ✅ MISSING METHOD 9: Set Message Reminder
  Future<void> _setMessageReminder(
      MessageChat message, String messageId) async {
    final reminderTime = await _pickTimeWithWheel();

    if (reminderTime != null) {
      final success = await _reminderProvider.scheduleReminder(
        userId: _currentUserId,
        messageId: messageId,
        conversationId: _groupChatId,
        reminderTime: reminderTime,
        message: message.content,
      );

      if (success) {
        Fluttertoast.showToast(msg: 'Reminder set successfully');
      } else {
        Fluttertoast.showToast(msg: 'Failed to set reminder');
      }
    }
  }

  // ✅ MISSING METHOD 10: Check Conversation Lock
  Future<void> _checkConversationLock() async {
    final lockStatus =
        await _lockProvider.getConversationLockStatus(_groupChatId);

    if (lockStatus != null && lockStatus['isLocked'] == true) {
      if (!mounted) return;

      final verified = await _showPINVerificationDialog();

      if (verified != true) {
        Navigator.pop(context);
      }
    }

    setState(() {
      _conversationLockedChecked = true;
    });
  }

  // ✅ MISSING METHOD 11: Show PIN Verification Dialog
  Future<bool> _showPINVerificationDialog() async {
    String? errorMessage;
    int remainingAttempts = 5;

    while (remainingAttempts > 0) {
      final pin = await showDialog<String>(
        context: context,
        barrierDismissible: false,
        builder: (context) => PINInputDialog(
          title: 'Enter PIN',
          onComplete: (pin) => Navigator.pop(context, pin),
          errorMessage: errorMessage,
          remainingAttempts: remainingAttempts,
        ),
      );

      if (pin == null) return false;

      final result = await _lockProvider.verifyPIN(
        conversationId: _groupChatId,
        enteredPin: pin,
      );

      if (result['success'] == true) {
        return true;
      }

      remainingAttempts = 5 - (result['failedAttempts'] as int);
      errorMessage = result['message'] as String;

      if (remainingAttempts <= 0 || result['locked'] == true) {
        await _lockProvider.autoDeleteMessagesAfterFailedAttempts(
          conversationId: _groupChatId,
        );
        Fluttertoast.showToast(
          msg: 'All messages deleted due to security breach',
          backgroundColor: Colors.red,
        );
        return false;
      }
    }

    return false;
  }

  // ✅ MISSING METHOD 12: Load Smart Replies
  Future<void> _loadSmartReplies() async {
    if (_listMessage.isEmpty) return;

    final lastMessage = _listMessage.first;
    final messageChat = MessageChat.fromDocument(lastMessage);

    if (messageChat.idFrom != _currentUserId &&
        messageChat.type == TypeMessage.text) {
      final replies =
          _smartReplyProvider.getRuleBasedReplies(messageChat.content);

      if (mounted) {
        setState(() {
          _smartReplies = replies;
          _lastReceivedMessage = messageChat.content;
        });
      }
    }
  }

  // ✅ MISSING METHOD 13: Show Reminders
  void _showReminders() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => Scaffold(
          appBar: AppBar(
            title: Text('Reminders'),
          ),
          body: StreamBuilder<QuerySnapshot>(
            stream: _reminderProvider.getUserReminders(_currentUserId),
            builder: (context, snapshot) {
              if (!snapshot.hasData) {
                return Center(child: CircularProgressIndicator());
              }

              final reminders = snapshot.data!.docs;

              if (reminders.isEmpty) {
                return Center(
                  child: Text('No reminders'),
                );
              }

              return ListView.builder(
                itemCount: reminders.length,
                itemBuilder: (context, index) {
                  final reminder =
                      MessageReminder.fromDocument(reminders[index]);

                  return ListTile(
                    title: Text(reminder.message),
                    subtitle: Text(
                      DateFormat('MMM dd, HH:mm').format(
                        DateTime.fromMillisecondsSinceEpoch(
                          int.parse(reminder.reminderTime),
                        ),
                      ),
                    ),
                    trailing: IconButton(
                      icon: Icon(Icons.delete, color: Colors.red),
                      onPressed: () {
                        _reminderProvider.deleteReminder(reminder.id);
                      },
                    ),
                  );
                },
              );
            },
          ),
        ),
      ),
    );
  }

  // ✅ MISSING METHOD 14: Build App Bar Actions
  List<Widget> _buildAppBarActions() {
    return [
      IconButton(
        icon: Icon(Icons.search),
        onPressed: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => SearchMessagesPage(
                groupChatId: _groupChatId,
                peerName: widget.arguments.peerNickname,
              ),
            ),
          );
        },
      ),
      IconButton(
        icon: Icon(Icons.notifications),
        onPressed: _showReminders,
      ),
      PopupMenuButton<String>(
        onSelected: (value) {
          switch (value) {
            case 'lock':
              _showLockOptions();
              break;
            case 'auto_delete':
              showDialog(
                context: context,
                builder: (_) => AutoDeleteSettingsDialog(
                  conversationId: _groupChatId,
                  provider: _autoDeleteProvider,
                ),
              );
              break;
          }
        },
        itemBuilder: (context) => [
          PopupMenuItem(
            value: 'lock',
            child: Row(
              children: [
                Icon(Icons.lock, color: ColorConstants.primaryColor),
                SizedBox(width: 8),
                Text('Lock Conversation'),
              ],
            ),
          ),
          PopupMenuItem(
            value: 'auto_delete',
            child: Row(
              children: [
                Icon(Icons.timer, color: ColorConstants.primaryColor),
                SizedBox(width: 8),
                Text('Auto-Delete Settings'),
              ],
            ),
          ),
        ],
      ),
    ];
  }

  // ✅ MISSING METHOD 15: Show Lock Options
  void _showLockOptions() async {
    final action = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Lock Conversation'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: Icon(Icons.lock_outline),
              title: Text('Set PIN'),
              onTap: () => Navigator.pop(context, 'set_pin'),
            ),
            ListTile(
              leading: Icon(Icons.lock_open),
              title: Text('Remove Lock'),
              onTap: () => Navigator.pop(context, 'remove'),
            ),
          ],
        ),
      ),
    );

    if (action == 'set_pin') {
      _showSetPINDialog();
    } else if (action == 'remove') {
      await _lockProvider.removeConversationLock(_groupChatId);
      Fluttertoast.showToast(msg: 'Lock removed');
    }
  }

  // ✅ MISSING METHOD 16: Show Set PIN Dialog
  void _showSetPINDialog() async {
    final pin = await showDialog<String>(
      context: context,
      builder: (context) => PINInputDialog(
        title: 'Set New PIN',
        onComplete: (pin) => Navigator.pop(context, pin),
      ),
    );

    if (pin != null) {
      _showConfirmPINDialog(pin);
    }
  }

  // ✅ MISSING METHOD 17: Show Confirm PIN Dialog
  void _showConfirmPINDialog(String originalPin) async {
    final confirmPin = await showDialog<String>(
      context: context,
      builder: (context) => PINInputDialog(
        title: 'Confirm PIN',
        onComplete: (pin) => Navigator.pop(context, pin),
      ),
    );

    if (confirmPin == originalPin) {
      final success = await _lockProvider.setConversationPIN(
        conversationId: _groupChatId,
        pin: originalPin,
      );

      if (success) {
        Fluttertoast.showToast(msg: 'PIN set successfully');
      }
    } else {
      Fluttertoast.showToast(msg: 'PINs do not match');
    }
  }

  // ✅ BUILD METHODS
  Widget _buildPinnedMessages() {
    if (_pinnedMessages.isEmpty) return SizedBox.shrink();

    return Container(
      height: 60,
      color: ColorConstants.greyColor2.withOpacity(0.3),
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        padding: EdgeInsets.symmetric(horizontal: 8),
        itemCount: _pinnedMessages.length,
        itemBuilder: (context, index) {
          final message = MessageChat.fromDocument(_pinnedMessages[index]);
          return GestureDetector(
            onTap: () {
              // Scroll to message
            },
            child: Container(
              margin: EdgeInsets.symmetric(horizontal: 4, vertical: 8),
              padding: EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  Icon(Icons.push_pin,
                      size: 16, color: ColorConstants.primaryColor),
                  SizedBox(width: 8),
                  Text(
                    message.content.length > 20
                        ? '${message.content.substring(0, 20)}...'
                        : message.content,
                  ),
                ],
              ),
            ),
          );
        },
      ),
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
                  if (_listMessage.isNotEmpty) {
                    return ListView.builder(
                      padding: EdgeInsets.all(10),
                      itemBuilder: (_, index) =>
                          _buildItemMessage(index, snapshot.data?.docs[index]),
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

  Widget _buildItemMessage(int index, DocumentSnapshot? document) {
    if (document == null) return SizedBox.shrink();

    final messageChat = MessageChat.fromDocument(document);
    final isMyMessage = messageChat.idFrom == _currentUserId;

    if (messageChat.type == TypeMessage.text) {
      return Container(
        margin: EdgeInsets.only(bottom: 10),
        child: Row(
          mainAxisAlignment:
              isMyMessage ? MainAxisAlignment.end : MainAxisAlignment.start,
          children: [
            GestureDetector(
              onLongPress: () =>
                  _showAdvancedMessageOptions(messageChat, document.id),
              child: Container(
                padding: EdgeInsets.all(12),
                constraints: BoxConstraints(maxWidth: 250),
                decoration: BoxDecoration(
                  color: isMyMessage
                      ? ColorConstants.primaryColor
                      : ColorConstants.greyColor2,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (messageChat.isDeleted)
                      Text(
                        messageChat.content,
                        style: TextStyle(
                          color: isMyMessage
                              ? Colors.white70
                              : ColorConstants.greyColor,
                          fontStyle: FontStyle.italic,
                        ),
                      )
                    else
                      Text(
                        messageChat.content,
                        style: TextStyle(
                          color: isMyMessage ? Colors.white : Colors.black87,
                        ),
                      ),
                    if (messageChat.editedAt != null)
                      Text(
                        '(edited)',
                        style: TextStyle(
                          fontSize: 10,
                          color: isMyMessage
                              ? Colors.white70
                              : ColorConstants.greyColor,
                        ),
                      ),
                    if (isMyMessage && !messageChat.isDeleted)
                      Padding(
                        padding: const EdgeInsets.only(top: 4),
                        child: ReadReceiptWidget(
                          isRead: messageChat.isRead,
                          size: 14,
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ],
        ),
      );
    } else if (messageChat.type == TypeMessage.image) {
      return Container(
        margin: EdgeInsets.only(bottom: 10),
        child: Row(
          mainAxisAlignment:
              isMyMessage ? MainAxisAlignment.end : MainAxisAlignment.start,
          children: [
            GestureDetector(
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => FullPhotoPage(url: messageChat.content),
                  ),
                );
              },
              onLongPress: () =>
                  _showAdvancedMessageOptions(messageChat, document.id),
              child: Container(
                clipBehavior: Clip.hardEdge,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Image.network(
                  messageChat.content,
                  width: 200,
                  height: 200,
                  fit: BoxFit.cover,
                  loadingBuilder: (_, child, loadingProgress) {
                    if (loadingProgress == null) return child;
                    return Container(
                      width: 200,
                      height: 200,
                      color: ColorConstants.greyColor2,
                      child: Center(
                        child: CircularProgressIndicator(
                          value: loadingProgress.expectedTotalBytes != null
                              ? loadingProgress.cumulativeBytesLoaded /
                                  loadingProgress.expectedTotalBytes!
                              : null,
                        ),
                      ),
                    );
                  },
                  errorBuilder: (_, __, ___) => Container(
                    width: 200,
                    height: 200,
                    color: ColorConstants.greyColor2,
                    child: Icon(Icons.error),
                  ),
                ),
              ),
            ),
          ],
        ),
      );
    } else {
      // Sticker
      return Container(
        margin: EdgeInsets.only(bottom: 10),
        child: Row(
          mainAxisAlignment:
              isMyMessage ? MainAxisAlignment.end : MainAxisAlignment.start,
          children: [
            GestureDetector(
              onLongPress: () =>
                  _showAdvancedMessageOptions(messageChat, document.id),
              child: Image.asset(
                'images/${messageChat.content}.gif',
                width: 100,
                height: 100,
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => Container(
                  width: 100,
                  height: 100,
                  color: ColorConstants.greyColor2,
                  child: Icon(Icons.error),
                ),
              ),
            ),
          ],
        ),
      );
    }
  }

  Widget _buildStickers() {
    return Container(
      decoration: BoxDecoration(
        border: Border(
          top: BorderSide(color: ColorConstants.greyColor2, width: 0.5),
        ),
        color: Colors.white,
      ),
      padding: EdgeInsets.symmetric(vertical: 8),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              _buildItemSticker("mimi1"),
              _buildItemSticker("mimi2"),
              _buildItemSticker("mimi3"),
            ],
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              _buildItemSticker("mimi4"),
              _buildItemSticker("mimi5"),
              _buildItemSticker("mimi6"),
            ],
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              _buildItemSticker("mimi7"),
              _buildItemSticker("mimi8"),
              _buildItemSticker("mimi9"),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildItemSticker(String stickerName) {
    return TextButton(
      onPressed: () =>
          _onSendMessageWithAutoDelete(stickerName, TypeMessage.sticker),
      child: Image.asset(
        'images/$stickerName.gif',
        width: 50,
        height: 50,
        fit: BoxFit.cover,
        errorBuilder: (_, __, ___) => Icon(Icons.error),
      ),
    );
  }

  Widget _buildAdvancedInput() {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (_smartReplies.isNotEmpty)
          SmartReplyWidget(
            replies: _smartReplies,
            onReplySelected: (reply) {
              _chatInputController.text = reply;
              setState(() => _smartReplies = []);
            },
          ),
        if (_replyingTo != null)
          Container(
            width: double.infinity,
            color: ColorConstants.greyColor2.withOpacity(0.2),
            padding: EdgeInsets.symmetric(horizontal: 8, vertical: 6),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    'Replying: ${_replyingTo!.content}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                IconButton(
                  icon: Icon(Icons.close, size: 18),
                  onPressed: () {
                    setState(() {
                      _replyingTo = null;
                    });
                  },
                ),
              ],
            ),
          ),
        Container(
          width: double.infinity,
          height: 50,
          decoration: BoxDecoration(
            border: Border(
              top: BorderSide(color: ColorConstants.greyColor2, width: 0.5),
            ),
            color: Colors.white,
          ),
          child: Row(
            children: [
              Material(
                color: Colors.white,
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
              ),
              Material(
                color: Colors.white,
                child: Container(
                  margin: EdgeInsets.symmetric(horizontal: 1),
                  child: IconButton(
                    icon: Icon(Icons.visibility_off),
                    onPressed: () {
                      showDialog(
                        context: context,
                        builder: (_) => SendViewOnceDialog(
                          onSend: (content, type) async {
                            await _viewOnceProvider.sendViewOnceMessage(
                              groupChatId: _groupChatId,
                              currentUserId: _currentUserId,
                              peerId: widget.arguments.peerId,
                              content: content,
                              type: type,
                            );
                            await _loadSmartReplies();
                          },
                        ),
                      );
                    },
                    color: ColorConstants.primaryColor,
                  ),
                ),
              ),
              Material(
                color: Colors.white,
                child: Container(
                  margin: EdgeInsets.symmetric(horizontal: 1),
                  child: IconButton(
                    icon: Icon(Icons.face),
                    onPressed: _getSticker,
                    color: ColorConstants.primaryColor,
                  ),
                ),
              ),
              Flexible(
                child: TextField(
                  onTapOutside: (_) {
                    Utilities.closeKeyboard();
                  },
                  onSubmitted: (_) {
                    _onSendMessageWithAutoDelete(
                      _chatInputController.text,
                      TypeMessage.text,
                    );
                  },
                  onChanged: (text) {
                    _handleTyping(text);
                    if (text.isNotEmpty && _smartReplies.isNotEmpty) {
                      setState(() => _smartReplies = []);
                    }
                  },
                  style: TextStyle(
                    color: ColorConstants.primaryColor,
                    fontSize: 15,
                  ),
                  controller: _chatInputController,
                  decoration: InputDecoration.collapsed(
                    hintText: 'Type your message...',
                    hintStyle: TextStyle(color: ColorConstants.greyColor),
                  ),
                  focusNode: _focusNode,
                ),
              ),
              Material(
                color: Colors.white,
                child: Container(
                  margin: EdgeInsets.symmetric(horizontal: 8),
                  child: IconButton(
                    icon: Icon(Icons.send),
                    onPressed: () => _onSendMessageWithAutoDelete(
                      _chatInputController.text,
                      TypeMessage.text,
                    ),
                    color: ColorConstants.primaryColor,
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  void _onBackPress() {
    if (_isShowSticker) {
      setState(() {
        _isShowSticker = false;
      });
    } else {
      _chatProvider.updateDataFirestore(
        FirestoreConstants.pathUserCollection,
        _currentUserId,
        {FirestoreConstants.chattingWith: null},
      );
      Navigator.pop(context);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: InkWell(
          onTap: () {
            // Navigate to user profile
          },
          child: Row(
            children: [
              AvatarWithStatus(
                userId: widget.arguments.peerId,
                photoUrl: widget.arguments.peerAvatar,
                size: 40,
                indicatorSize: 12,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.arguments.peerNickname,
                      style: TextStyle(
                        color: ColorConstants.primaryColor,
                        fontSize: 16,
                      ),
                    ),
                    UserStatusIndicator(
                      userId: widget.arguments.peerId,
                      showText: true,
                      size: 8,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        centerTitle: false,
        actions: _buildAppBarActions(),
      ),
      body: SafeArea(
        child: PopScope(
          canPop: false,
          onPopInvokedWithResult: (didPop, result) {
            if (didPop) return;
            _onBackPress();
          },
          child: Stack(
            children: [
              Column(
                children: [
                  _buildPinnedMessages(),
                  _buildListMessage(),
                  _buildTypingIndicator(),
                  _isShowSticker ? _buildStickers() : SizedBox.shrink(),
                  _buildAdvancedInput(),
                ],
              ),
              Positioned(
                child: _isLoading ? LoadingView() : SizedBox.shrink(),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    _unreadMessagesSubscription?.cancel();
    _pinnedSub?.cancel();
    _typingTimer?.cancel();

    if (_presenceProvider != null) {
      _presenceProvider!.setUserOffline(_currentUserId);
      _presenceProvider!.setTypingStatus(
        conversationId: _groupChatId,
        userId: _currentUserId,
        isTyping: false,
      );
    }

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

  ChatPageArguments({
    required this.peerId,
    required this.peerAvatar,
    required this.peerNickname,
  });
}
