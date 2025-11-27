import 'dart:async';
import 'dart:io';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_chat_demo/constants/constants.dart';
import 'package:flutter_chat_demo/models/models.dart';
import 'package:flutter_chat_demo/pages/pages.dart';
import 'package:flutter_chat_demo/providers/providers.dart';
import 'package:flutter_chat_demo/services/services.dart';
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

class ChatPageState extends State<ChatPage> with WidgetsBindingObserver {
  late final String _currentUserId;
  UserPresenceProvider? _presenceProvider;
  ChatBubbleService? _bubbleService;

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

  late TextEditingController _chatInputController;
  late ScrollController _listScrollController;
  late FocusNode _focusNode;
  bool _isDisposed = false;

  late ChatProvider _chatProvider;
  late AuthProvider _authProvider;
  late MessageProvider _messageProvider;
  late ReactionProvider _reactionProvider;
  late ReminderProvider _reminderProvider;
  late AutoDeleteProvider _autoDeleteProvider;
  late ConversationLockProvider _lockProvider;
  late ViewOnceProvider _viewOnceProvider;
  late SmartReplyProvider _smartReplyProvider;
  VoiceMessageProvider? _voiceProvider;
  LocationProvider? _locationProvider;
  TranslationProvider? _translationProvider;

  List<DocumentSnapshot> _pinnedMessages = [];
  StreamSubscription<QuerySnapshot>? _pinnedSub;

  List<SmartReply> _smartReplies = [];
  String _lastReceivedMessage = '';

  MessageChat? _replyingTo;
  bool _conversationLockedChecked = false;

  StreamSubscription<QuerySnapshot>? _unreadMessagesSubscription;
  StreamSubscription<QuerySnapshot>? _incomingMessagesSubscription;
  StreamSubscription? _miniChatSubscription;

  bool _showFeaturesMenu = false;

  bool _isRecording = false;
  String _recordingDuration = "0:00";
  Timer? _recordingTimer;
  int _recordingSeconds = 0;

  final Map<String, Timer> _scheduledMessages = {};
  final Map<String, String> _scheduledMessageContents = {};

  @override
  void initState() {
    super.initState();

    // ✅ FIX: Initialize controllers in initState
    _chatInputController = TextEditingController();
    _listScrollController = ScrollController();
    _focusNode = FocusNode();

    WidgetsBinding.instance.addObserver(this);
    _focusNode.addListener(_onFocusChange);
    _listScrollController.addListener(_scrollListener);

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_isDisposed) {
        _initializeProviders(context);
      }
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    if (state == AppLifecycleState.paused) {
      _presenceProvider?.setUserOffline(_currentUserId);
    } else if (state == AppLifecycleState.resumed) {
      _presenceProvider?.setUserOnline(_currentUserId);
    }
  }

  void _initializeProviders(BuildContext context) {
    if (_isDisposed) return;

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
    _bubbleService = context.read<ChatBubbleService>();

    _miniChatSubscription = _bubbleService?.miniChatMessageStream.listen(
      (message) {
        if (message.userId == widget.arguments.peerId) {
          print('💬 Message from mini chat: ${message.message}');

          // Show notification
          Fluttertoast.showToast(
            msg: '📨 ${widget.arguments.peerNickname}: ${message.message}',
            backgroundColor: Colors.green,
            toastLength: Toast.LENGTH_SHORT,
          );
        }
      },
      onError: (error) {
        print('❌ Mini chat stream error: $error');
      },
    );

    try {
      _voiceProvider = VoiceMessageProvider(
        firebaseStorage: _chatProvider.firebaseStorage,
      );
    } catch (e) {
      print('⚠️ Voice provider initialization failed: $e');
      _voiceProvider = null;
    }

    _locationProvider = LocationProvider();
    _translationProvider = TranslationProvider();

    _readLocal();
    _loadPinnedMessages();
    _checkConversationLock();
    _loadSmartReplies();
    _setupAutoReadMarking();

    if (_presenceProvider != null && _currentUserId.isNotEmpty) {
      _presenceProvider!.setUserOnline(_currentUserId);
      _presenceProvider!.markMessagesAsRead(
        conversationId: _groupChatId,
        userId: _currentUserId,
      );
    }

    ErrorLogger.logScreenView('chat_page');
  }

  void _scrollListener() {
    if (_isDisposed || !_listScrollController.hasClients) return;
    final pos = _listScrollController.position;
    if (pos.pixels >= pos.maxScrollExtent - 100 &&
        !_listScrollController.position.outOfRange &&
        _limit <= _listMessage.length) {
      if (mounted) {
        setState(() {
          _limit += _limitIncrement;
        });
      }
    }
  }

  void _onFocusChange() {
    if (_isDisposed || !mounted) return;
    if (_focusNode.hasFocus) {
      setState(() {
        _isShowSticker = false;
        _showFeaturesMenu = false;
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

    // ✅ THÊM DÒNG NÀY - Setup listener SAU KHI có _groupChatId
    _setupIncomingMessageListener();

    _chatProvider.updateDataFirestore(
      FirestoreConstants.pathUserCollection,
      _currentUserId,
      {FirestoreConstants.chattingWith: peerId},
    );

    Future.delayed(Duration(milliseconds: 500), () {
      if (!_isDisposed) {
        _markMessagesAsRead();
      }
    });
  }

  void _loadPinnedMessages() {
    _pinnedSub?.cancel();
    _pinnedSub = _messageProvider.getPinnedMessages(_groupChatId).listen(
      (snapshot) {
        if (!mounted || _isDisposed) return;
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
        if (!mounted || _isDisposed) return false;
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
    if (_isDisposed) return;
    _focusNode.unfocus();
    setState(() {
      _isShowSticker = !_isShowSticker;
      _showFeaturesMenu = false;
    });
  }

  void _handleTyping(String text) {
    if (_presenceProvider == null || _isDisposed) return;

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
      if (!_isDisposed) {
        _isTyping = false;
        _presenceProvider?.setTypingStatus(
          conversationId: _groupChatId,
          userId: _currentUserId,
          isTyping: false,
        );
      }
    });
  }

  void _setupIncomingMessageListener() {
    _incomingMessagesSubscription?.cancel();

    if (_groupChatId.isEmpty || _currentUserId.isEmpty) {
      print('⚠️ Cannot setup listener: groupChatId or currentUserId is empty');
      return;
    }

    _incomingMessagesSubscription = FirebaseFirestore.instance
        .collection(FirestoreConstants.pathMessageCollection)
        .doc(_groupChatId)
        .collection(_groupChatId)
        .where(FirestoreConstants.idTo, isEqualTo: _currentUserId)
        .where('isRead', isEqualTo: false)
        .snapshots()
        .listen(
      (snapshot) {
        for (var change in snapshot.docChanges) {
          if (change.type == DocumentChangeType.added) {
            _showChatBubbleIfNeeded();
          }
        }
      },
      onError: (error) {
        ErrorLogger.logError(
          error,
          null,
          context: 'Incoming Messages Listener',
        );
      },
    );
  }

  Future<void> _showChatBubbleIfNeeded() async {
    final lifecycleState = WidgetsBinding.instance.lifecycleState;

    if (lifecycleState != AppLifecycleState.resumed) {
      await _bubbleService?.showChatBubble(
        userId: widget.arguments.peerId,
        userName: widget.arguments.peerNickname,
        avatarUrl: widget.arguments.peerAvatar,
      );
    }
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
    _unreadMessagesSubscription?.cancel();
    _unreadMessagesSubscription = FirebaseFirestore.instance
        .collection(FirestoreConstants.pathMessageCollection)
        .doc(_groupChatId)
        .collection(_groupChatId)
        .where(FirestoreConstants.idTo, isEqualTo: _currentUserId)
        .where('isRead', isEqualTo: false)
        .snapshots()
        .listen(
      (snapshot) {
        if (snapshot.docs.isNotEmpty && !_isDisposed) {
          _markMessagesAsRead();
        }
      },
      onError: (error) {
        ErrorLogger.logError(error, null, context: 'Setup Auto Read');
      },
    );
  }

  Future<void> _uploadFile() async {
    if (_imageFile == null) return;

    try {
      final fileName = DateTime.now().millisecondsSinceEpoch.toString();
      final uploadTask = _chatProvider.uploadFile(_imageFile!, fileName);
      final snapshot = await uploadTask;
      _imageUrl = await snapshot.ref.getDownloadURL();

      if (!mounted || _isDisposed) return;
      setState(() {
        _isLoading = false;
      });

      await _onSendMessageWithAutoDelete(_imageUrl, TypeMessage.image);
    } catch (e) {
      ErrorLogger.logError(e, null, context: 'Upload File');

      if (mounted && !_isDisposed) {
        setState(() {
          _isLoading = false;
        });
      }
      Fluttertoast.showToast(msg: 'Upload failed');
    }
  }

  Future<void> _onSendMessageWithAutoDelete(String content, int type) async {
    if (_isDisposed) return;

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

    if (!_isDisposed && _chatInputController.hasListeners) {
      _chatInputController.clear();
    }

    if (mounted && !_isDisposed) {
      setState(() {
        _replyingTo = null;
        _smartReplies = [];
      });
    }

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

    if (!_isDisposed) {
      await _loadSmartReplies();
    }

    if (_listScrollController.hasClients && !_isDisposed) {
      _listScrollController.animateTo(
        0,
        duration: Duration(milliseconds: 300),
        curve: Curves.easeOut,
      );
    }
  }

  Future<void> _markMessagesAsRead() async {
    if (_isDisposed) return;

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
    } catch (e) {
      ErrorLogger.logError(e, null, context: 'Mark Messages Read');
    }
  }

  void _showAdvancedMessageOptions(MessageChat message, String messageId) {
    if (_isDisposed) return;

    showModalBottomSheet(
      context: context,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) => EnhancedMessageOptionsDialog(
        isOwnMessage: message.idFrom == _currentUserId,
        isPinned: message.isPinned,
        isDeleted: message.isDeleted,
        messageContent: message.content,
        onEdit: () => _editMessage(messageId, message.content),
        onDelete: () => _deleteMessage(messageId),
        onPin: () => _togglePinMessage(messageId, message.isPinned),
        onCopy: () => _copyMessage(message.content),
        onReply: () => _setReplyToMessage(message),
        onReminder: () => _setMessageReminder(message, messageId),
        onTranslate: () => _translateMessage(message.content),
      ),
    );
  }

  Future<void> _editMessage(String messageId, String currentContent) async {
    if (_isDisposed) return;

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

  Future<void> _deleteMessage(String messageId) async {
    if (_isDisposed) return;

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

    if (confirm == true && !_isDisposed) {
      final success = await _messageProvider.deleteMessage(
        _groupChatId,
        messageId,
      );
      if (success) {
        Fluttertoast.showToast(msg: 'Message deleted');
      }
    }
  }

  Future<void> _togglePinMessage(String messageId, bool currentStatus) async {
    if (_isDisposed) return;

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

  void _copyMessage(String content) {
    Clipboard.setData(ClipboardData(text: content));
    Fluttertoast.showToast(msg: 'Copied to clipboard');
  }

  void _setReplyToMessage(MessageChat message) {
    if (_isDisposed || !mounted) return;
    setState(() {
      _replyingTo = message;
    });
    _focusNode.requestFocus();
  }

  void _showReactionPicker(String messageId) {
    if (_isDisposed) return;

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

  Future<DateTime?> _pickTimeWithWheel() async {
    if (_isDisposed) return null;

    DateTime selectedTime = DateTime.now().add(Duration(hours: 1));

    return await showDialog<DateTime>(
      context: context,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setState) {
            return AlertDialog(
              title: Text('Set Reminder Time'),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  ListTile(
                    title: Text('Date'),
                    subtitle: Text(
                      DateFormat('MMM dd, yyyy').format(selectedTime),
                    ),
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
  }

  Future<void> _setMessageReminder(
    MessageChat message,
    String messageId,
  ) async {
    if (_isDisposed) return;

    final reminderTime = await _pickTimeWithWheel();

    if (reminderTime != null && !_isDisposed) {
      final success = await _reminderProvider.scheduleReminder(
        userId: _currentUserId,
        messageId: messageId,
        conversationId: _groupChatId,
        reminderTime: reminderTime,
        message: message.content,
      );

      if (success) {
        Fluttertoast.showToast(msg: '⏰ Reminder set successfully');
      } else {
        Fluttertoast.showToast(msg: 'Failed to set reminder');
      }
    }
  }

  Future<void> _translateMessage(String content) async {
    if (_translationProvider == null || _isDisposed) return;

    showDialog(
      context: context,
      builder: (context) => TranslationDialog(
        originalText: content,
        translationProvider: _translationProvider!,
      ),
    );
  }

  Future<void> _checkConversationLock() async {
    if (_isDisposed) return;

    final lockStatus = await _lockProvider.getConversationLockStatus(
      _groupChatId,
    );

    if (lockStatus != null && lockStatus['isLocked'] == true) {
      if (!mounted || _isDisposed) return;

      final verified = await _showPINVerificationDialog();

      if (verified != true && mounted) {
        Navigator.pop(context);
      }
    }

    if (mounted && !_isDisposed) {
      setState(() {
        _conversationLockedChecked = true;
      });
    }
  }

  Future<bool> _showPINVerificationDialog() async {
    if (_isDisposed) return false;

    String? errorMessage;
    int remainingAttempts = 5;

    while (remainingAttempts > 0 && !_isDisposed) {
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

      if (pin == null || _isDisposed) return false;

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

  Future<void> _loadSmartReplies() async {
    if (_listMessage.isEmpty || _isDisposed) return;

    final lastMessage = _listMessage.first;
    final messageChat = MessageChat.fromDocument(lastMessage);

    if (messageChat.idFrom != _currentUserId &&
        messageChat.type == TypeMessage.text) {
      final replies = _smartReplyProvider.getRuleBasedReplies(
        messageChat.content,
      );

      if (mounted && !_isDisposed) {
        setState(() {
          _smartReplies = replies;
          _lastReceivedMessage = messageChat.content;
        });
      }
    }
  }

  void _showReminders() {
    if (_isDisposed) return;

    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => Scaffold(
          appBar: AppBar(title: Text('Reminders')),
          body: StreamBuilder<QuerySnapshot>(
            stream: _reminderProvider.getUserReminders(_currentUserId),
            builder: (context, snapshot) {
              if (!snapshot.hasData) {
                return Center(child: CircularProgressIndicator());
              }

              final reminders = snapshot.data!.docs;

              if (reminders.isEmpty) {
                return Center(child: Text('No reminders'));
              }

              return ListView.builder(
                itemCount: reminders.length,
                itemBuilder: (context, index) {
                  final reminder = MessageReminder.fromDocument(
                    reminders[index],
                  );

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

  Future<void> _createChatBubble() async {
    if (_bubbleService == null || _isDisposed) {
      Fluttertoast.showToast(msg: 'Bubble service not available');
      return;
    }

    final hasPermission = await _bubbleService!.hasOverlayPermission();
    if (!hasPermission) {
      final granted = await _bubbleService!.requestOverlayPermission();
      if (!granted) {
        Fluttertoast.showToast(msg: 'Overlay permission required');
        return;
      }
    }

    final choice = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Create Chat Bubble'),
        content: Text('Choose how to open this conversation:'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, 'bubble'),
            child: Text('Bubble Only'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, 'minichat'),
            child: Text('Mini Chat'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Cancel'),
          ),
        ],
      ),
    );

    if (choice == null) return;

    if (choice == 'bubble') {
      // Create bubble only
      final success = await _bubbleService!.showChatBubble(
        userId: widget.arguments.peerId,
        userName: widget.arguments.peerNickname,
        avatarUrl: widget.arguments.peerAvatar,
      );

      if (success) {
        Fluttertoast.showToast(
          msg: '💬 Chat bubble created',
          backgroundColor: Colors.green,
        );
      }
    } else if (choice == 'minichat') {
      // Show mini chat directly
      final success = await _bubbleService!.showMiniChat(
        userId: widget.arguments.peerId,
        userName: widget.arguments.peerNickname,
        avatarUrl: widget.arguments.peerAvatar,
      );

      if (success) {
        Fluttertoast.showToast(
          msg: '💬 Mini chat opened',
          backgroundColor: Colors.green,
        );
      }
    }
  }

  List<Widget> _buildAppBarActions() {
    return [
      // Video Call button
      IconButton(
        icon: Icon(Icons.videocam, color: ColorConstants.primaryColor),
        onPressed: () {
          Fluttertoast.showToast(
            msg: '🎥 Video Call feature coming soon!',
            backgroundColor: ColorConstants.primaryColor,
          );
        },
        tooltip: 'Video Call',
      ),

      // Voice Call button
      IconButton(
        icon: Icon(Icons.phone, color: ColorConstants.primaryColor),
        onPressed: () {
          Fluttertoast.showToast(
            msg: '📞 Voice Call feature coming soon!',
            backgroundColor: ColorConstants.primaryColor,
          );
        },
        tooltip: 'Voice Call',
      ),

      // More options menu
      IconButton(
        icon: Icon(Icons.more_vert),
        onPressed: _showChatOptionsMenu,
        tooltip: 'More options',
      ),
    ];
  }

  void _showChatOptionsMenu() {
    if (_isDisposed) return;

    showModalBottomSheet(
      context: context,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) => Container(
        padding: const EdgeInsets.symmetric(vertical: 8),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Search Messages
            ListTile(
              leading: Icon(Icons.search, color: ColorConstants.primaryColor),
              title: Text('Search Messages'),
              subtitle: Text('Search in conversation'),
              onTap: () {
                Navigator.pop(context);
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

            // Show Reminders
            ListTile(
              leading:
                  Icon(Icons.notifications, color: ColorConstants.primaryColor),
              title: Text('Reminders'),
              subtitle: Text('View all reminders'),
              onTap: () {
                Navigator.pop(context);
                _showReminders();
              },
            ),
          ],
        ),
      ),
    );
  }

  void _showLockOptions() async {
    if (_isDisposed) return;

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

    if (action == 'set_pin' && !_isDisposed) {
      _showSetPINDialog();
    } else if (action == 'remove' && !_isDisposed) {
      await _lockProvider.removeConversationLock(_groupChatId);
      Fluttertoast.showToast(msg: 'Lock removed');
    }
  }

  void _showSetPINDialog() async {
    if (_isDisposed) return;

    final pin = await showDialog<String>(
      context: context,
      builder: (context) => PINInputDialog(
        title: 'Set New PIN',
        onComplete: (pin) => Navigator.pop(context, pin),
      ),
    );

    if (pin != null && !_isDisposed) {
      _showConfirmPINDialog(pin);
    }
  }

  void _showConfirmPINDialog(String originalPin) async {
    if (_isDisposed) return;

    final confirmPin = await showDialog<String>(
      context: context,
      builder: (context) => PINInputDialog(
        title: 'Confirm PIN',
        onComplete: (pin) => Navigator.pop(context, pin),
      ),
    );

    if (confirmPin == originalPin && !_isDisposed) {
      final success = await _lockProvider.setConversationPIN(
        conversationId: _groupChatId,
        pin: originalPin,
      );

      if (success) {
        Fluttertoast.showToast(msg: 'PIN set successfully');
      }
    } else if (confirmPin != null) {
      Fluttertoast.showToast(msg: 'PINs do not match');
    }
  }

  void _toggleFeaturesMenu() {
    if (_isDisposed || !mounted) return;
    setState(() {
      _showFeaturesMenu = !_showFeaturesMenu;
      _isShowSticker = false;
    });
  }

  Widget _buildFeaturesMenu() {
    if (!_showFeaturesMenu) return SizedBox.shrink();

    return Container(
      constraints: BoxConstraints(maxHeight: 110),
      padding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: ColorConstants.greyColor2)),
      ),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: [
            _buildFeatureButton(
              icon: Icons.visibility_off,
              label: 'View Once',
              onTap: () {
                setState(() => _showFeaturesMenu = false);
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
            ),
            _buildFeatureButton(
              icon: Icons.timer,
              label: 'Delete',
              onTap: () {
                setState(() => _showFeaturesMenu = false);
                showDialog(
                  context: context,
                  builder: (_) => AutoDeleteSettingsDialog(
                    conversationId: _groupChatId,
                    provider: _autoDeleteProvider,
                  ),
                );
              },
            ),
            _buildFeatureButton(
              icon: Icons.lock,
              label: 'Lock',
              onTap: () {
                setState(() => _showFeaturesMenu = false);
                _showLockOptions();
              },
            ),
            _buildFeatureButton(
              icon: Icons.location_on,
              label: 'Location',
              onTap: () {
                setState(() => _showFeaturesMenu = false);
                _shareLocation();
              },
            ),
            _buildFeatureButton(
              icon: Icons.schedule_send,
              label: 'Schedule',
              onTap: () {
                setState(() => _showFeaturesMenu = false);
                _scheduleMessage();
              },
            ),
            _buildFeatureButton(
              icon: Icons.bubble_chart,
              label: 'Bubble',
              onTap: () {
                setState(() => _showFeaturesMenu = false);
                _createChatBubble();
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFeatureButton({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: () {
        if (_isDisposed) return;
        setState(() => _showFeaturesMenu = false);
        onTap();
      },
      child: Container(
        width: 70, // ✅ FIX: Fixed width
        padding: EdgeInsets.symmetric(horizontal: 4, vertical: 8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, color: ColorConstants.primaryColor, size: 26),
            SizedBox(height: 4),
            Text(
              label,
              style: TextStyle(
                fontSize: 11, // ✅ FIX: Smaller font
                color: ColorConstants.primaryColor,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _shareLocation() async {
    if (_locationProvider == null || _isDisposed) return;

    if (mounted) setState(() => _isLoading = true);

    // ✅ FIX: Get location with full details
    final locationData =
        await _locationProvider!.getCurrentLocationWithDetails();

    if (mounted && !_isDisposed) setState(() => _isLoading = false);

    if (locationData != null && !_isDisposed) {
      // ✅ FIX: Use formatLocationMessage from provider
      final message = _locationProvider!.formatLocationMessage(locationData);

      await _onSendMessageWithAutoDelete(message, TypeMessage.text);
      Fluttertoast.showToast(msg: '📍 Location shared');
    } else {
      Fluttertoast.showToast(msg: 'Failed to get location');
    }
  }

  Future<void> _scheduleMessage() async {
    if (_isDisposed) return;

    final result = await showDialog<Map<String, dynamic>>(
      context: context,
      barrierDismissible: false,
      builder: (context) => ScheduleMessageDialog(),
    );

    if (result == null || _isDisposed || !mounted) return;

    final messageText = result['message'] as String;
    final scheduledTime = result['time'] as DateTime;

    final delay = scheduledTime.difference(DateTime.now());

    if (delay.isNegative) {
      if (mounted) {
        Fluttertoast.showToast(msg: 'Invalid time');
      }
      return;
    }

    final scheduleKey = scheduledTime.millisecondsSinceEpoch.toString();

    _scheduledMessageContents[scheduleKey] = messageText;

    final timer = Timer(delay, () {
      if (!_isDisposed && mounted) {
        final content = _scheduledMessageContents[scheduleKey];
        if (content != null) {
          _onSendMessageWithAutoDelete(content, TypeMessage.text);
        }
        _scheduledMessages.remove(scheduleKey);
        _scheduledMessageContents.remove(scheduleKey);
      }
    });

    _scheduledMessages[scheduleKey] = timer;

    if (mounted) {
      Fluttertoast.showToast(
        msg:
            '📅 Message scheduled for ${DateFormat('HH:mm').format(scheduledTime)}',
        backgroundColor: Colors.green,
      );
    }
  }

  Future<void> _startRecording() async {
    if (_voiceProvider == null || _isDisposed) {
      Fluttertoast.showToast(msg: 'Voice recording not available');
      return;
    }

    final initialized = await _voiceProvider!.initRecorder();
    if (!initialized) {
      Fluttertoast.showToast(msg: 'Microphone permission required');
      return;
    }

    final started = await _voiceProvider!.startRecording();
    if (started && mounted && !_isDisposed) {
      setState(() {
        _isRecording = true;
        _recordingSeconds = 0;
        _recordingDuration = "0:00";
      });

      _recordingTimer = Timer.periodic(Duration(seconds: 1), (timer) {
        if (!mounted || _isDisposed) {
          timer.cancel();
          return;
        }
        setState(() {
          _recordingSeconds++;
          final minutes = _recordingSeconds ~/ 60;
          final seconds = _recordingSeconds % 60;
          _recordingDuration = "$minutes:${seconds.toString().padLeft(2, '0')}";
        });
      });
    }
  }

  Future<void> _stopRecording() async {
    if (_voiceProvider == null || _isDisposed) return;

    _recordingTimer?.cancel();

    final filePath = await _voiceProvider!.stopRecording();
    if (filePath == null) {
      if (mounted && !_isDisposed) setState(() => _isRecording = false);
      Fluttertoast.showToast(msg: 'Recording failed');
      return;
    }

    if (mounted && !_isDisposed) {
      setState(() {
        _isRecording = false;
        _isLoading = true;
      });
    }

    final fileName = 'voice_${DateTime.now().millisecondsSinceEpoch}.aac';
    final url = await _voiceProvider!.uploadVoiceMessage(filePath, fileName);

    if (mounted && !_isDisposed) setState(() => _isLoading = false);

    if (url != null && !_isDisposed) {
      await _onSendMessageWithAutoDelete(url, 3);
      Fluttertoast.showToast(msg: '🎤 Voice message sent');
    } else {
      Fluttertoast.showToast(msg: 'Failed to send voice message');
    }
  }

  Future<void> _cancelRecording() async {
    if (_voiceProvider == null || _isDisposed) return;

    _recordingTimer?.cancel();
    await _voiceProvider!.cancelRecording();
    if (mounted && !_isDisposed) setState(() => _isRecording = false);
  }

  Widget _buildPinnedMessages() {
    if (_pinnedMessages.isEmpty) return SizedBox.shrink();

    return Container(
      height: 60,
      color: ColorConstants.greyColor2.withOpacity(0.3),
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        padding: EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        itemCount: _pinnedMessages.length,
        itemExtent: 180, // ✅ FIX: Fixed width for performance
        itemBuilder: (context, index) {
          final message = MessageChat.fromDocument(_pinnedMessages[index]);
          return GestureDetector(
            onTap: () {
              // TODO: Scroll to message
            },
            child: Container(
              width: 170, // ✅ FIX: Explicit width
              margin: EdgeInsets.symmetric(horizontal: 4),
              padding: EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    Icons.push_pin,
                    size: 14,
                    color: ColorConstants.primaryColor,
                  ),
                  SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      message.content,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(fontSize: 12),
                    ),
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

    final data = document.data() as Map<String, dynamic>?;
    final isViewOnce = data?['isViewOnce'] ?? false;
    final isViewed = data?['isViewed'] ?? false;

    if (isViewOnce) {
      return Container(
        margin: EdgeInsets.only(bottom: 10),
        child: Row(
          mainAxisAlignment:
              isMyMessage ? MainAxisAlignment.end : MainAxisAlignment.start,
          children: [
            ViewOnceMessageWidget(
              groupChatId: _groupChatId,
              messageId: document.id,
              content: messageChat.content,
              type: messageChat.type,
              currentUserId: _currentUserId,
              isViewed: isViewed,
              provider: _viewOnceProvider,
            ),
          ],
        ),
      );
    }

    // Voice Message
    if (messageChat.type == 3 && _voiceProvider != null) {
      return Container(
        margin: EdgeInsets.only(bottom: 10),
        child: Row(
          mainAxisAlignment:
              isMyMessage ? MainAxisAlignment.end : MainAxisAlignment.start,
          children: [
            VoiceMessageWidget(
              voiceUrl: messageChat.content,
              isMyMessage: isMyMessage,
              voiceProvider: _voiceProvider!,
            ),
          ],
        ),
      );
    }

    // Text Message
    if (messageChat.type == TypeMessage.text) {
      final location =
          _locationProvider?.parseLocationFromMessage(messageChat.content);

      return Container(
        margin: EdgeInsets.only(bottom: 10),
        child: Column(
          crossAxisAlignment:
              isMyMessage ? CrossAxisAlignment.end : CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment:
                  isMyMessage ? MainAxisAlignment.end : MainAxisAlignment.start,
              children: [
                GestureDetector(
                  onLongPress: () =>
                      _showAdvancedMessageOptions(messageChat, document.id),
                  onDoubleTap: () => _showReactionPicker(document.id),
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
                        else if (location != null)
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(
                                    Icons.location_on,
                                    color:
                                        isMyMessage ? Colors.white : Colors.red,
                                    size: 20,
                                  ),
                                  SizedBox(width: 4),
                                  Text(
                                    'Location',
                                    style: TextStyle(
                                      color: isMyMessage
                                          ? Colors.white
                                          : Colors.black87,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ],
                              ),
                              SizedBox(height: 4),
                              Text(
                                messageChat.content.split('\n').first,
                                style: TextStyle(
                                  color: isMyMessage
                                      ? Colors.white
                                      : Colors.black87,
                                  fontSize: 12,
                                ),
                              ),
                            ],
                          )
                        else
                          Text(
                            messageChat.content,
                            style: TextStyle(
                              color:
                                  isMyMessage ? Colors.white : Colors.black87,
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
                if (!messageChat.isDeleted) ...[
                  SizedBox(width: 4),
                  Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        icon: Icon(Icons.add_reaction, size: 18),
                        onPressed: () => _showReactionPicker(document.id),
                        padding: EdgeInsets.zero,
                        constraints: BoxConstraints(),
                      ),
                      if (!isMyMessage)
                        IconButton(
                          icon: Icon(Icons.alarm_add, size: 18),
                          onPressed: () =>
                              _setMessageReminder(messageChat, document.id),
                          padding: EdgeInsets.zero,
                          constraints: BoxConstraints(),
                        ),
                    ],
                  ),
                ],
              ],
            ),
            StreamBuilder<QuerySnapshot>(
              stream: _reactionProvider.getReactions(_groupChatId, document.id),
              builder: (context, snapshot) {
                if (!snapshot.hasData || snapshot.data!.docs.isEmpty) {
                  return SizedBox.shrink();
                }

                final reactions = <String, int>{};
                final userReactions = <String, bool>{};

                for (var doc in snapshot.data!.docs) {
                  final data = doc.data() as Map<String, dynamic>;
                  final emoji = data['emoji'] as String;
                  final userId = data['userId'] as String;

                  reactions[emoji] = (reactions[emoji] ?? 0) + 1;
                  if (userId == _currentUserId) {
                    userReactions[emoji] = true;
                  }
                }

                return Padding(
                  padding: const EdgeInsets.only(top: 4),
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
          ],
        ),
      );
    }
    // Image Message
    else if (messageChat.type == TypeMessage.image) {
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
    }
    // Sticker
    else {
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
        mainAxisSize: MainAxisSize.min,
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
        // Smart Replies - ✅ FIX: Wrap in SingleChildScrollView
        if (_smartReplies.isNotEmpty)
          Container(
            constraints: BoxConstraints(maxHeight: 60), // ✅ FIX: Add max height
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: SmartReplyWidget(
                replies: _smartReplies,
                onReplySelected: (reply) {
                  if (!_isDisposed) {
                    _chatInputController.text = reply;
                    setState(() => _smartReplies = []);
                  }
                },
              ),
            ),
          ),

        // Reply indicator
        if (_replyingTo != null)
          Container(
            width: double.infinity,
            constraints: BoxConstraints(maxHeight: 50), // ✅ FIX: Add max height
            color: ColorConstants.greyColor2.withOpacity(0.2),
            padding: EdgeInsets.symmetric(horizontal: 8, vertical: 6),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    'Replying: ${_replyingTo!.content}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(fontSize: 13), // ✅ FIX: Smaller font
                  ),
                ),
                IconButton(
                  icon: Icon(Icons.close, size: 18),
                  onPressed: () {
                    if (mounted && !_isDisposed) {
                      setState(() => _replyingTo = null);
                    }
                  },
                  padding: EdgeInsets.zero, // ✅ FIX: Remove padding
                  constraints: BoxConstraints(minWidth: 32, minHeight: 32),
                ),
              ],
            ),
          ),

        // Recording indicator
        if (_isRecording)
          Container(
            width: double.infinity,
            padding: EdgeInsets.all(12),
            color: Colors.red.withOpacity(0.1),
            child: Row(
              children: [
                Icon(Icons.fiber_manual_record, color: Colors.red, size: 16),
                SizedBox(width: 8),
                Text(
                  'Recording... $_recordingDuration',
                  style: TextStyle(
                    color: Colors.red,
                    fontWeight: FontWeight.bold,
                    fontSize: 13, // ✅ FIX: Smaller font
                  ),
                ),
                Spacer(),
                IconButton(
                  icon: Icon(Icons.delete, color: Colors.red, size: 20),
                  onPressed: _cancelRecording,
                  padding: EdgeInsets.zero,
                  constraints: BoxConstraints(minWidth: 36, minHeight: 36),
                ),
                IconButton(
                  icon: Icon(
                    Icons.send,
                    color: ColorConstants.primaryColor,
                    size: 20,
                  ),
                  onPressed: _stopRecording,
                  padding: EdgeInsets.zero,
                  constraints: BoxConstraints(minWidth: 36, minHeight: 36),
                ),
              ],
            ),
          ),

        // ✅ FIX: Input area with proper constraints
        Container(
          width: double.infinity,
          constraints: BoxConstraints(
            minHeight: 50,
            maxHeight: 120, // ✅ FIX: Prevent overflow when typing long text
          ),
          decoration: BoxDecoration(
            border: Border(
              top: BorderSide(color: ColorConstants.greyColor2, width: 0.5),
            ),
            color: Colors.white,
          ),
          child: Row(
            crossAxisAlignment:
                CrossAxisAlignment.end, // ✅ FIX: Align to bottom
            children: [
              // More options button
              Material(
                color: Colors.white,
                child: Container(
                  margin: EdgeInsets.symmetric(horizontal: 1),
                  child: IconButton(
                    icon: Icon(
                      _showFeaturesMenu ? Icons.close : Icons.more_horiz,
                      color: ColorConstants.primaryColor,
                      size: 24,
                    ),
                    onPressed: _toggleFeaturesMenu,
                    padding: EdgeInsets.all(8), // ✅ FIX: Proper padding
                    constraints: BoxConstraints(minWidth: 40, minHeight: 40),
                  ),
                ),
              ),

              // Image picker
              Material(
                color: Colors.white,
                child: IconButton(
                  icon: Icon(Icons.image, size: 24),
                  onPressed: () {
                    _pickImage().then((isSuccess) {
                      if (isSuccess) _uploadFile();
                    });
                  },
                  color: ColorConstants.primaryColor,
                  padding: EdgeInsets.all(8),
                  constraints: BoxConstraints(minWidth: 40, minHeight: 40),
                ),
              ),

              // Sticker button
              Material(
                color: Colors.white,
                child: IconButton(
                  icon: Icon(Icons.face, size: 24),
                  onPressed: _getSticker,
                  color: ColorConstants.primaryColor,
                  padding: EdgeInsets.all(8),
                  constraints: BoxConstraints(minWidth: 40, minHeight: 40),
                ),
              ),

              // ✅ FIX: Text input with proper constraints
              Expanded(
                child: Container(
                  constraints: BoxConstraints(
                    minHeight: 40,
                    maxHeight: 100, // ✅ FIX: Limit input height
                  ),
                  padding: EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  child: TextField(
                    onTapOutside: (_) {
                      Utilities.closeKeyboard();
                    },
                    onSubmitted: (_) {
                      if (!_isDisposed) {
                        _onSendMessageWithAutoDelete(
                          _chatInputController.text,
                          TypeMessage.text,
                        );
                      }
                    },
                    onChanged: (text) {
                      _handleTyping(text);
                      if (text.isNotEmpty &&
                          _smartReplies.isNotEmpty &&
                          mounted &&
                          !_isDisposed) {
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
                    maxLines: 4, // ✅ FIX: Limit to 4 lines
                    minLines: 1,
                    textInputAction: TextInputAction.newline,
                  ),
                ),
              ),

              // Voice button
              if (!_isRecording && _voiceProvider != null)
                Material(
                  color: Colors.white,
                  child: IconButton(
                    icon: Icon(Icons.mic, size: 24),
                    onPressed: _startRecording,
                    color: ColorConstants.primaryColor,
                    padding: EdgeInsets.all(8),
                    constraints: BoxConstraints(minWidth: 40, minHeight: 40),
                  ),
                ),

              // Send button
              Material(
                color: Colors.white,
                child: IconButton(
                  icon: Icon(Icons.send, size: 24),
                  onPressed: () {
                    if (!_isDisposed) {
                      _onSendMessageWithAutoDelete(
                        _chatInputController.text,
                        TypeMessage.text,
                      );
                    }
                  },
                  color: ColorConstants.primaryColor,
                  padding: EdgeInsets.all(8),
                  constraints: BoxConstraints(minWidth: 40, minHeight: 40),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  void _onBackPress() {
    if (_isShowSticker || _showFeaturesMenu) {
      if (mounted && !_isDisposed) {
        setState(() {
          _isShowSticker = false;
          _showFeaturesMenu = false;
        });
      }
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
          onTap: () async {
            if (_isDisposed) return;
            final userDoc = await FirebaseFirestore.instance
                .collection(FirestoreConstants.pathUserCollection)
                .doc(widget.arguments.peerId)
                .get();

            if (userDoc.exists && mounted && !_isDisposed) {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) =>
                      UserProfilePage(userChat: UserChat.fromDocument(userDoc)),
                ),
              );
            }
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
                      overflow: TextOverflow.ellipsis,
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
                  if (_isShowSticker) _buildStickers(),
                  _buildFeaturesMenu(),
                  _buildAdvancedInput(),
                ],
              ),
              Positioned(child: _isLoading ? LoadingView() : SizedBox.shrink()),
            ],
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    _isDisposed = true;

    // Cancel all scheduled messages
    _scheduledMessages.forEach((key, timer) {
      timer.cancel();
    });
    _scheduledMessages.clear();
    _scheduledMessageContents.clear();

    _unreadMessagesSubscription?.cancel();
    _incomingMessagesSubscription?.cancel();
    _pinnedSub?.cancel();
    _typingTimer?.cancel();
    _recordingTimer?.cancel();
    _miniChatSubscription?.cancel();

    if (_presenceProvider != null && _currentUserId.isNotEmpty) {
      _presenceProvider!.setUserOffline(_currentUserId);
      _presenceProvider!.setTypingStatus(
        conversationId: _groupChatId,
        userId: _currentUserId,
        isTyping: false,
      );
    }

    _voiceProvider?.dispose();

    if (!_chatInputController.hasListeners) {
      try {
        _chatInputController.dispose();
      } catch (e) {
        print('⚠️ Controller already disposed: $e');
      }
    }

    if (_listScrollController.hasClients) {
      try {
        _listScrollController.removeListener(_scrollListener);
        _listScrollController.dispose();
      } catch (e) {
        print('⚠️ ScrollController error: $e');
      }
    }

    try {
      _focusNode.removeListener(_onFocusChange);
      _focusNode.dispose();
    } catch (e) {
      print('⚠️ FocusNode error: $e');
    }

    WidgetsBinding.instance.removeObserver(this);
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
