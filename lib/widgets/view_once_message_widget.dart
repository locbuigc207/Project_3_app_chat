// View Once Message Widget
import 'package:flutter/material.dart';
import 'package:flutter_chat_demo/constants/constants.dart';
import 'package:flutter_chat_demo/providers/providers.dart';

class ViewOnceMessageWidget extends StatefulWidget {
  final String groupChatId;
  final String messageId;
  final String content;
  final int type;
  final String currentUserId;
  final bool isViewed;
  final ViewOnceProvider provider;

  const ViewOnceMessageWidget({
    super.key,
    required this.groupChatId,
    required this.messageId,
    required this.content,
    required this.type,
    required this.currentUserId,
    required this.isViewed,
    required this.provider,
  });

  @override
  State<ViewOnceMessageWidget> createState() => _ViewOnceMessageWidgetState();
}

class _ViewOnceMessageWidgetState extends State<ViewOnceMessageWidget> {
  bool _isRevealed = false;
  int _countdown = 10;

  void _revealMessage() async {
    setState(() => _isRevealed = true);

    // Mark as viewed
    await widget.provider.markAsViewed(
      groupChatId: widget.groupChatId,
      messageId: widget.messageId,
      userId: widget.currentUserId,
    );

    // Start countdown
    for (int i = 10; i > 0; i--) {
      await Future.delayed(const Duration(seconds: 1));
      if (mounted) {
        setState(() => _countdown = i - 1);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (widget.isViewed && !_isRevealed) {
      return Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: ColorConstants.greyColor2,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: const [
            Icon(Icons.visibility_off, color: ColorConstants.greyColor),
            SizedBox(width: 8),
            Text(
              'This message was opened',
              style: TextStyle(
                color: ColorConstants.greyColor,
                fontStyle: FontStyle.italic,
              ),
            ),
          ],
        ),
      );
    }

    if (!_isRevealed) {
      return GestureDetector(
        onTap: _revealMessage,
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: ColorConstants.primaryColor.withOpacity(0.1),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: ColorConstants.primaryColor,
              width: 2,
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: const [
              Icon(Icons.visibility, color: ColorConstants.primaryColor),
              SizedBox(width: 8),
              Text(
                'Tap to view once',
                style: TextStyle(
                  color: ColorConstants.primaryColor,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
        ),
      );
    }

    return Stack(
      children: [
        // Message Content
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: ColorConstants.greyColor2,
            borderRadius: BorderRadius.circular(12),
          ),
          child: widget.type == TypeMessage.text
              ? Text(widget.content)
              : widget.type == TypeMessage.image
              ? Image.network(
            widget.content,
            width: 200,
            height: 200,
            fit: BoxFit.cover,
          )
              : const SizedBox.shrink(),
        ),

        // Countdown Overlay
        Positioned(
          top: 8,
          right: 8,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: Colors.black.withOpacity(0.7),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(
                  Icons.timer,
                  size: 16,
                  color: Colors.white,
                ),
                const SizedBox(width: 4),
                Text(
                  '$_countdown',
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

// Send View Once Dialog
class SendViewOnceDialog extends StatefulWidget {
  final Function(String content, int type) onSend;

  const SendViewOnceDialog({
    super.key,
    required this.onSend,
  });

  @override
  State<SendViewOnceDialog> createState() => _SendViewOnceDialogState();
}

class _SendViewOnceDialogState extends State<SendViewOnceDialog> {
  final _textController = TextEditingController();
  bool _isText = true;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text(
        'Send View Once Message',
        style: TextStyle(color: ColorConstants.primaryColor),
      ),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text(
            'This message will disappear after being opened',
            style: TextStyle(
              color: ColorConstants.greyColor,
              fontSize: 14,
            ),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: () {
                    setState(() => _isText = true);
                  },
                  icon: const Icon(Icons.text_fields),
                  label: const Text('Text'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: _isText
                        ? ColorConstants.primaryColor
                        : ColorConstants.greyColor2,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: () {
                    setState(() => _isText = false);
                  },
                  icon: const Icon(Icons.image),
                  label: const Text('Image'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: !_isText
                        ? ColorConstants.primaryColor
                        : ColorConstants.greyColor2,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          if (_isText)
            TextField(
              controller: _textController,
              maxLines: 3,
              decoration: const InputDecoration(
                hintText: 'Type your message...',
                border: OutlineInputBorder(),
              ),
            ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        TextButton(
          onPressed: () {
            if (_isText && _textController.text.trim().isNotEmpty) {
              widget.onSend(
                _textController.text.trim(),
                TypeMessage.text,
              );
              Navigator.pop(context);
            }
          },
          child: const Text('Send'),
        ),
      ],
    );
  }

  @override
  void dispose() {
    _textController.dispose();
    super.dispose();
  }
}