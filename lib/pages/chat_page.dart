// chat_page.dart (FIXED VERSION)
import 'dart:async';
import 'dart:io';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/cupertino.dart';
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
  // Basic state from original file
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

  // Providers
  late ChatProvider _chatProvider;
  late AuthProvider _authProvider;
  late MessageProvider _messageProvider;
  late ReactionProvider _reactionProvider;
  late ReminderProvider _reminderProvider;
  late AutoDeleteProvider _autoDeleteProvider;
  late ConversationLockProvider _lockProvider;
  late ViewOnceProvider _viewOnceProvider;
  late SmartReplyProvider _smartReplyProvider;

  // Pinned messages
  List<DocumentSnapshot> _pinnedMessages = [];
  StreamSubscription<QuerySnapshot>? _pinnedSub;

  // Smart replies
  List<SmartReply> _smartReplies = [];
  String _lastReceivedMessage = '';

  // Reply feature UI
  MessageChat? _replyingTo;

  // Conversation lock checked flag
  bool _conversationLockedChecked = false;

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

    _readLocal();
    _loadPinnedMessages();
    _checkConversationLock();
    _loadSmartReplies();
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
  }

  void _loadPinnedMessages() {
    _pinnedSub?.cancel();
    _pinnedSub =
        _messageProvider.getPinnedMessages(_groupChatId).listen((snapshot) {
      if (!mounted) return;
      setState(() {
        _pinnedMessages = snapshot.docs;
      });
    }, onError: (err) {
      // Handle error silently
    });
  }

  Future<bool> _pickImage() async {
    final imagePicker = ImagePicker();
    final pickedXFile = await imagePicker
        .pickImage(source: ImageSource.gallery)
        .catchError((err) {
      Fluttertoast.showToast(msg: err.toString());
      return null;
    });
    if (pickedXFile != null) {
      final imageFile = File(pickedXFile.path);
      if (!mounted) return false;
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
    if (_imageFile == null) return;
    final fileName = DateTime.now().millisecondsSinceEpoch.toString();
    final uploadTask = _chatProvider.uploadFile(_imageFile!, fileName);
    try {
      final snapshot = await uploadTask;
      _imageUrl = await snapshot.ref.getDownloadURL();
      if (!mounted) return;
      setState(() {
        _isLoading = false;
      });
      await _onSendMessageWithAutoDelete(_imageUrl, TypeMessage.image);
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
      Fluttertoast.showToast(msg: e.toString());
    }
  }

  Future<void> _onSendMessageWithAutoDelete(String content, int type) async {
    if (content.trim().isEmpty) {
      Fluttertoast.showToast(
          msg: 'Nothing to send', backgroundColor: ColorConstants.greyColor);
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
      _chatProvider.sendMessage(finalContent, type, _groupChatId,
          _currentUserId, widget.arguments.peerId);
    } catch (e) {
      Fluttertoast.showToast(msg: 'Send failed: $e');
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
      // Ignore auto-delete errors
    }

    await _loadSmartReplies();

    if (_listScrollController.hasClients) {
      _listScrollController.animateTo(0,
          duration: Duration(milliseconds: 300), curve: Curves.easeOut);
    }
  }

  void _showAdvancedMessageOptions(DocumentSnapshot doc) {
    final message = MessageChat.fromDocument(doc);
    final isOwnMessage = message.idFrom == _currentUserId;

    showModalBottomSheet(
      context: context,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (isOwnMessage) ...[
              ListTile(
                leading: Icon(Icons.edit),
                title: Text('Edit'),
                onTap: () {
                  Navigator.pop(context);
                  _editMessage(doc);
                },
              ),
              ListTile(
                leading: Icon(Icons.delete),
                title: Text('Delete'),
                onTap: () {
                  Navigator.pop(context);
                  _deleteMessage(doc.id);
                },
              ),
            ],
            ListTile(
              leading: Icon(Icons.alarm),
              title: Text('Set Reminder'),
              onTap: () {
                Navigator.pop(context);
                _setMessageReminder(doc);
              },
            ),
            ListTile(
              leading: Icon(
                  message.isPinned ? Icons.push_pin : Icons.push_pin_outlined),
              title: Text(message.isPinned ? 'Unpin' : 'Pin'),
              onTap: () {
                Navigator.pop(context);
                _togglePinMessage(doc);
              },
            ),
            ListTile(
              leading: Icon(Icons.copy),
              title: Text('Copy'),
              onTap: () {
                Navigator.pop(context);
                _copyMessage(message.content);
              },
            ),
            ListTile(
              leading: Icon(Icons.reply),
              title: Text('Reply'),
              onTap: () {
                Navigator.pop(context);
                _setReplyToMessage(message);
              },
            ),
            ListTile(
              leading: Icon(Icons.emoji_emotions),
              title: Text('React'),
              onTap: () {
                Navigator.pop(context);
                _showReactionPicker(doc);
              },
            ),
          ],
        ),
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
    final success =
        await _messageProvider.deleteMessage(_groupChatId, messageId);
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
      Fluttertoast.showToast(
          msg: message.isPinned ? 'Message unpinned' : 'Message pinned');
    }
  }

  void _copyMessage(String content) {
    Clipboard.setData(ClipboardData(text: content));
    Fluttertoast.showToast(msg: 'Message copied');
  }

  void _setReplyToMessage(MessageChat message) {
    setState(() {
      _replyingTo = message;
    });
    Fluttertoast.showToast(msg: 'Replying to message');
    FocusScope.of(context).requestFocus(_focusNode);
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

  Future<TimeOfDay?> _pickTimeWithWheel(BuildContext context) async {
    TimeOfDay selectedTime = TimeOfDay.now();

    return await showModalBottomSheet<TimeOfDay>(
      context: context,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (context) {
        return SizedBox(
          height: 250,
          child: Column(
            children: [
              Expanded(
                child: CupertinoDatePicker(
                  mode: CupertinoDatePickerMode.time,
                  use24hFormat: true,
                  initialDateTime: DateTime.now(),
                  onDateTimeChanged: (dt) {
                    selectedTime = TimeOfDay(hour: dt.hour, minute: dt.minute);
                  },
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(8.0),
                child: ElevatedButton(
                  child: const Text("Select"),
                  onPressed: () {
                    Navigator.pop(context, selectedTime);
                  },
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Future<void> _setMessageReminder(DocumentSnapshot doc) async {
    final message = MessageChat.fromDocument(doc);

    final DateTime? pickedDate = await showDatePicker(
      context: context,
      initialDate: DateTime.now().add(Duration(hours: 1)),
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(Duration(days: 365)),
    );

    if (pickedDate != null) {
      final TimeOfDay? pickedTime = await _pickTimeWithWheel(context);

      if (pickedTime != null) {
        final reminderTime = DateTime(
          pickedDate.year,
          pickedDate.month,
          pickedDate.day,
          pickedTime.hour,
          pickedTime.minute,
        );

        try {
          final success = await _reminderProvider.scheduleReminder(
            userId: _currentUserId,
            messageId: doc.id,
            conversationId: _groupChatId,
            reminderTime: reminderTime,
            message: message.content,
          );

          if (success) {
            Fluttertoast.showToast(msg: 'Reminder set!');
          } else {
            Fluttertoast.showToast(msg: 'Failed to set reminder');
          }
        } catch (e) {
          Fluttertoast.showToast(msg: 'Error scheduling reminder');
        }
      }
    }
  }

  // CRITICAL FIX for _checkConversationLock and _showPINVerificationDialog
// Replace these methods in chat_page.dart

  Future<void> _checkConversationLock() async {
    if (_conversationLockedChecked) {
      print('✅ Already checked lock status');
      return;
    }

    try {
      print('🔍 Checking conversation lock...');

      final lockStatus =
          await _lockProvider.getConversationLockStatus(_groupChatId);

      if (lockStatus == null || lockStatus['isLocked'] != true) {
        print('✅ No lock found or conversation not locked');
        _conversationLockedChecked = true;
        return;
      }

      print('🔒 Conversation is locked');

      // Check if temporarily locked
      if (lockStatus['temporarilyLocked'] == true) {
        final lockedUntil = lockStatus['lockedUntil'] as DateTime?;
        if (lockedUntil != null && mounted) {
          final remaining =
              lockedUntil.difference(DateTime.now()).inMinutes + 1;

          await showDialog(
            context: context,
            barrierDismissible: false,
            builder: (context) => AlertDialog(
              title: Row(
                children: [
                  Icon(Icons.lock_clock, color: Colors.red),
                  SizedBox(width: 8),
                  Text('Locked'),
                ],
              ),
              content: Text(
                'Too many failed attempts.\nTry again in $remaining minutes.',
              ),
              actions: [
                TextButton(
                  onPressed: () {
                    Navigator.pop(context); // Close dialog
                    Navigator.pop(context); // Exit chat page
                  },
                  child: Text('OK'),
                ),
              ],
            ),
          );
          return;
        }
      }

      // Show PIN input dialog
      print('🔑 Showing PIN verification dialog...');
      await _showPINVerificationDialog(
        failedAttempts: lockStatus['failedAttempts'] ?? 0,
      );
    } catch (e) {
      print('❌ Error checking lock: $e');
      _conversationLockedChecked = true;
    }
  }

  Future<void> _showPINVerificationDialog({int failedAttempts = 0}) async {
    if (!mounted) return;

    print('🔐 Showing PIN dialog with $failedAttempts failed attempts');

    final result = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (context) => WillPopScope(
        onWillPop: () async => false, // Prevent back button
        child: PINInputDialog(
          title: 'Enter PIN to unlock',
          errorMessage: failedAttempts > 0 ? 'Previous attempt failed' : null,
          remainingAttempts: failedAttempts > 0 ? (5 - failedAttempts) : null,
          onComplete: (pin) async {
            print('🔑 Verifying PIN: $pin');

            // Verify PIN
            final verifyResult = await _lockProvider.verifyPIN(
              conversationId: _groupChatId,
              enteredPin: pin,
            );

            print('🔍 Verify result: $verifyResult');

            if (!mounted) return pin;

            if (verifyResult['success'] == true) {
              // PIN correct - CRITICAL: Close dialog with success
              print('✅ PIN verified, closing dialog');
              Navigator.pop(context, true); // Return true for success

              if (mounted) {
                _conversationLockedChecked = true;
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Row(
                      children: [
                        Icon(Icons.check_circle, color: Colors.white),
                        SizedBox(width: 8),
                        Text('Access granted'),
                      ],
                    ),
                    backgroundColor: Colors.green,
                    duration: Duration(seconds: 1),
                  ),
                );
              }
            } else {
              // PIN incorrect
              print('❌ PIN incorrect');
              Navigator.pop(context, false); // Return false for failure

              final newFailedAttempts = verifyResult['failedAttempts'] as int;

              // Check if should auto-delete (5 failed attempts)
              if (newFailedAttempts >= 5) {
                if (mounted) {
                  // Show warning dialog
                  await showDialog(
                    context: context,
                    barrierDismissible: false,
                    builder: (context) => AlertDialog(
                      title: Row(
                        children: [
                          Icon(Icons.warning, color: Colors.red),
                          SizedBox(width: 8),
                          Text('Security Alert'),
                        ],
                      ),
                      content: Text(
                        'Too many failed attempts.\nAll messages will be deleted for security.',
                        style: TextStyle(color: Colors.red),
                      ),
                      actions: [
                        TextButton(
                          onPressed: () {
                            Navigator.pop(context);
                          },
                          child: Text('OK'),
                        ),
                      ],
                    ),
                  );

                  // Auto-delete messages
                  print('🗑️ Triggering auto-delete...');
                  await _lockProvider.autoDeleteMessagesAfterFailedAttempts(
                    conversationId: _groupChatId,
                  );

                  if (mounted) {
                    Navigator.pop(context); // Exit chat page
                  }
                }
                return pin;
              }

              // Show error and retry
              if (mounted) {
                await Future.delayed(Duration(milliseconds: 300));
                await _showPINVerificationDialog(
                  failedAttempts: newFailedAttempts,
                );
              }
            }

            return pin;
          },
        ),
      ),
    );

    // Handle dialog result
    if (result != true && mounted) {
      print('❌ PIN verification failed or cancelled');
      Navigator.pop(context); // Exit chat page
    } else {
      print('✅ PIN verification successful');
    }
  }

  Future<void> _loadSmartReplies() async {
    try {
      final messages = await FirebaseFirestore.instance
          .collection(FirestoreConstants.pathMessageCollection)
          .doc(_groupChatId)
          .collection(_groupChatId)
          .where(FirestoreConstants.idFrom, isEqualTo: widget.arguments.peerId)
          .orderBy(FirestoreConstants.timestamp, descending: true)
          .limit(1)
          .get();

      if (messages.docs.isNotEmpty) {
        final lastMessage = MessageChat.fromDocument(messages.docs.first);

        if (lastMessage.content != _lastReceivedMessage) {
          _lastReceivedMessage = lastMessage.content;
          final replies = await _smartReplyProvider.getSmartReplies(
            message: lastMessage.content,
            conversationHistory: [],
          );
          if (!mounted) return;
          setState(() {
            _smartReplies = replies;
          });
        }
      } else {
        if (!mounted) return;
        setState(() {
          _smartReplies = [];
        });
      }
    } catch (e) {
      // Ignore smart reply failures
    }
  }

  void _showReminders() {
    showModalBottomSheet(
      context: context,
      builder: (context) => StreamBuilder<QuerySnapshot>(
        stream: _reminderProvider.getUserReminders(_currentUserId),
        builder: (_, snapshot) {
          if (!snapshot.hasData) {
            return Center(child: CircularProgressIndicator());
          }

          final reminders = snapshot.data!.docs;

          if (reminders.isEmpty) {
            return Center(
                child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Text('No reminders'),
            ));
          }

          return ListView.builder(
            itemCount: reminders.length,
            itemBuilder: (_, index) {
              final reminder = MessageReminder.fromDocument(reminders[index]);
              final reminderTime = DateTime.fromMillisecondsSinceEpoch(
                  int.parse(reminder.reminderTime));
              return ListTile(
                leading: Icon(Icons.alarm),
                title: Text(reminder.message),
                subtitle:
                    Text(DateFormat('MMM dd, HH:mm').format(reminderTime)),
                trailing: IconButton(
                  icon: Icon(Icons.check),
                  onPressed: () async {
                    await _reminderProvider.completeReminder(reminder.id);
                  },
                ),
              );
            },
          );
        },
      ),
    );
  }

  List<Widget> _buildAppBarActions() {
    return [
      IconButton(
        icon: Icon(Icons.search),
        onPressed: () async {
          await Navigator.push(
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
      PopupMenuButton<String>(
        onSelected: (value) async {
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
            case 'view_reminders':
              _showReminders();
              break;
          }
        },
        itemBuilder: (context) => [
          PopupMenuItem(
            value: 'lock',
            child: Row(
              children: [
                Icon(Icons.lock),
                SizedBox(width: 8),
                Text('Lock Settings'),
              ],
            ),
          ),
          PopupMenuItem(
            value: 'auto_delete',
            child: Row(
              children: [
                Icon(Icons.auto_delete),
                SizedBox(width: 8),
                Text('Auto-Delete Messages'),
              ],
            ),
          ),
          PopupMenuItem(
            value: 'view_reminders',
            child: Row(
              children: [
                Icon(Icons.notifications),
                SizedBox(width: 8),
                Text('View Reminders'),
              ],
            ),
          ),
        ],
      ),
    ];
  }

// Add this new method for lock options:
  void _showLockOptions() {
    showModalBottomSheet(
      context: context,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Icon(Icons.security, color: ColorConstants.primaryColor),
                  SizedBox(width: 12),
                  Text(
                    'Conversation Security',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: ColorConstants.primaryColor,
                    ),
                  ),
                ],
              ),
            ),
            ListTile(
              leading: Icon(Icons.lock, color: ColorConstants.primaryColor),
              title: Text('Set PIN Lock'),
              subtitle: Text('Protect with 4-digit PIN'),
              onTap: () {
                Navigator.pop(context);
                _showSetPINDialog();
              },
            ),
            ListTile(
              leading: Icon(Icons.lock_open, color: Colors.red),
              title: Text('Remove Lock'),
              subtitle: Text('Remove PIN protection'),
              onTap: () async {
                Navigator.pop(context);

                // Confirm removal
                final confirm = await showDialog<bool>(
                  context: context,
                  builder: (context) => AlertDialog(
                    title: Text('Remove Lock?'),
                    content: Text(
                        'This will remove PIN protection from this conversation.'),
                    actions: [
                      TextButton(
                        onPressed: () => Navigator.pop(context, false),
                        child: Text('Cancel'),
                      ),
                      TextButton(
                        onPressed: () => Navigator.pop(context, true),
                        child:
                            Text('Remove', style: TextStyle(color: Colors.red)),
                      ),
                    ],
                  ),
                );

                if (confirm == true) {
                  final success =
                      await _lockProvider.removeConversationLock(_groupChatId);
                  if (success && mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text('Lock removed'),
                        backgroundColor: Colors.orange,
                      ),
                    );
                  }
                }
              },
            ),
            Divider(),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text(
                'Note: After 5 failed PIN attempts, all messages will be automatically deleted for security.',
                style: TextStyle(
                  fontSize: 12,
                  color: Colors.red,
                  fontStyle: FontStyle.italic,
                ),
                textAlign: TextAlign.center,
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showSetPINDialog() {
    String? firstPin;

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => PINInputDialog(
        title: 'Set PIN (4 digits)',
        onComplete: (pin) async {
          firstPin = pin;
          Navigator.pop(context);
          _showConfirmPINDialog(firstPin!);
          return pin;
        },
      ),
    );
  }

  void _showConfirmPINDialog(String firstPin) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => PINInputDialog(
        title: 'Confirm PIN',
        onComplete: (pin) async {
          Navigator.pop(context);

          if (pin == firstPin) {
            final success = await _lockProvider.setConversationPIN(
              conversationId: _groupChatId,
              pin: pin,
            );

            if (success && mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Row(
                    children: [
                      Icon(Icons.check_circle, color: Colors.white),
                      SizedBox(width: 8),
                      Text('PIN lock enabled'),
                    ],
                  ),
                  backgroundColor: Colors.green,
                ),
              );
            }
          } else {
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text('PINs do not match. Please try again.'),
                  backgroundColor: Colors.red,
                ),
              );
            }
          }
          return pin;
        },
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
                      _onSendMessageWithAutoDelete(
                          _chatInputController.text, TypeMessage.text);
                    },
                    onChanged: (text) {
                      if (text.isNotEmpty && _smartReplies.isNotEmpty) {
                        setState(() => _smartReplies = []);
                      }
                    },
                    style: TextStyle(
                        color: ColorConstants.primaryColor, fontSize: 15),
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
                    onPressed: () => _onSendMessageWithAutoDelete(
                        _chatInputController.text, TypeMessage.text),
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
            border: Border(
                top: BorderSide(color: ColorConstants.greyColor2, width: 0.5)),
            color: Colors.white,
          ),
        ),
      ],
    );
  }

  Widget _buildItemMessage(int index, DocumentSnapshot? document) {
    if (document == null) return SizedBox.shrink();
    final messageChat = MessageChat.fromDocument(document);

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
      onLongPress: () => _showAdvancedMessageOptions(document),
      child: Row(
        mainAxisAlignment:
            isMyMessage ? MainAxisAlignment.end : MainAxisAlignment.start,
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
                                ? loadingProgress.cumulativeBytesLoaded /
                                    loadingProgress.expectedTotalBytes!
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
              crossAxisAlignment: isMyMessage
                  ? CrossAxisAlignment.end
                  : CrossAxisAlignment.start,
              children: [
                Container(
                  constraints: BoxConstraints(
                      maxWidth: MediaQuery.of(context).size.width * 0.7),
                  child: messageChat.type == TypeMessage.text
                      ? Container(
                          padding: EdgeInsets.fromLTRB(15, 10, 15, 10),
                          decoration: BoxDecoration(
                            color: isMyMessage
                                ? ColorConstants.greyColor2
                                : ColorConstants.primaryColor,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                messageChat.content,
                                style: TextStyle(
                                  color: isMyMessage
                                      ? ColorConstants.primaryColor
                                      : Colors.white,
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
                              decoration: BoxDecoration(
                                  borderRadius: BorderRadius.circular(8)),
                              child: GestureDetector(
                                child: Image.network(
                                  messageChat.content,
                                  loadingBuilder: (_, child, loadingProgress) {
                                    if (loadingProgress == null) return child;
                                    return Container(
                                      decoration: BoxDecoration(
                                        color: ColorConstants.greyColor2,
                                        borderRadius: BorderRadius.all(
                                            Radius.circular(8)),
                                      ),
                                      width: 200,
                                      height: 200,
                                      child: Center(
                                        child: CircularProgressIndicator(
                                          color: ColorConstants.themeColor,
                                          value: loadingProgress
                                                      .expectedTotalBytes !=
                                                  null
                                              ? loadingProgress
                                                      .cumulativeBytesLoaded /
                                                  loadingProgress
                                                      .expectedTotalBytes!
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
                                      builder: (_) => FullPhotoPage(
                                          url: messageChat.content),
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
                StreamBuilder<QuerySnapshot>(
                  stream:
                      _reactionProvider.getReactions(_groupChatId, document.id),
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
                if (_isLastMessageLeft(index) || _isLastMessageRight(index))
                  Padding(
                    padding: EdgeInsets.only(
                        top: 5,
                        left: isMyMessage ? 0 : 50,
                        right: isMyMessage ? 10 : 0),
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
                          _formatTimestamp(messageChat.timestamp),
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

  String _formatTimestamp(String ts) {
    int? ms = int.tryParse(ts);
    if (ms == null) {
      final d = double.tryParse(ts);
      if (d != null) ms = (d * 1000).toInt();
    }
    final dt = DateTime.fromMillisecondsSinceEpoch(
        ms ?? DateTime.now().millisecondsSinceEpoch);
    return DateFormat('dd MMM HH:mm').format(dt);
  }

  bool _isLastMessageLeft(int index) {
    if ((index > 0 &&
            _listMessage[index - 1].get(FirestoreConstants.idFrom) ==
                _currentUserId) ||
        index == 0) {
      return true;
    } else {
      return false;
    }
  }

  bool _isLastMessageRight(int index) {
    if ((index > 0 &&
            _listMessage[index - 1].get(FirestoreConstants.idFrom) !=
                _currentUserId) ||
        index == 0) {
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
        border: Border(
            top: BorderSide(color: ColorConstants.greyColor2, width: 0.5)),
        color: Colors.white,
      ),
      padding: EdgeInsets.symmetric(vertical: 8),
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
                  if (_listMessage.length > 0) {
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(
          widget.arguments.peerNickname,
          style: TextStyle(color: ColorConstants.primaryColor),
        ),
        centerTitle: true,
        actions: _buildAppBarActions(),
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
                  _buildAdvancedInput(),
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

  @override
  void dispose() {
    _pinnedSub?.cancel();
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

  ChatPageArguments(
      {required this.peerId,
      required this.peerAvatar,
      required this.peerNickname});
}
