// PIN Input Dialog
import 'package:flutter/material.dart';
import 'package:flutter_chat_demo/constants/constants.dart';

class PINInputDialog extends StatefulWidget {
  final String title;
  final Function(String pin) onComplete;
  final bool isConfirm;

  const PINInputDialog({
    super.key,
    required this.title,
    required this.onComplete,
    this.isConfirm = false,
  });

  @override
  State<PINInputDialog> createState() => _PINInputDialogState();
}

class _PINInputDialogState extends State<PINInputDialog> {
  String _pin = '';
  final int _pinLength = 4;

  void _onNumberPressed(String number) {
    if (_pin.length < _pinLength) {
      setState(() {
        _pin += number;
      });

      if (_pin.length == _pinLength) {
        Future.delayed(const Duration(milliseconds: 200), () {
          widget.onComplete(_pin);
        });
      }
    }
  }

  void _onDeletePressed() {
    if (_pin.isNotEmpty) {
      setState(() {
        _pin = _pin.substring(0, _pin.length - 1);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20),
      ),
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              widget.title,
              style: const TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.bold,
                color: ColorConstants.primaryColor,
              ),
            ),
            const SizedBox(height: 32),

            // PIN Display
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(_pinLength, (index) {
                return Container(
                  margin: const EdgeInsets.symmetric(horizontal: 8),
                  width: 16,
                  height: 16,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: index < _pin.length
                        ? ColorConstants.primaryColor
                        : ColorConstants.greyColor2,
                  ),
                );
              }),
            ),

            const SizedBox(height: 32),

            // Number Pad
            GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 3,
                childAspectRatio: 1.5,
                crossAxisSpacing: 16,
                mainAxisSpacing: 16,
              ),
              itemCount: 12,
              itemBuilder: (context, index) {
                if (index == 9) {
                  return const SizedBox.shrink();
                } else if (index == 10) {
                  return _buildNumberButton('0');
                } else if (index == 11) {
                  return _buildDeleteButton();
                } else {
                  return _buildNumberButton('${index + 1}');
                }
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNumberButton(String number) {
    return InkWell(
      onTap: () => _onNumberPressed(number),
      borderRadius: BorderRadius.circular(40),
      child: Container(
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          border: Border.all(
            color: ColorConstants.greyColor2,
            width: 2,
          ),
        ),
        child: Center(
          child: Text(
            number,
            style: const TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.bold,
              color: ColorConstants.primaryColor,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildDeleteButton() {
    return InkWell(
      onTap: _onDeletePressed,
      borderRadius: BorderRadius.circular(40),
      child: Container(
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          border: Border.all(
            color: ColorConstants.greyColor2,
            width: 2,
          ),
        ),
        child: const Center(
          child: Icon(
            Icons.backspace_outlined,
            color: ColorConstants.primaryColor,
          ),
        ),
      ),
    );
  }
}

// Lock Settings Dialog
class ConversationLockSettingsDialog extends StatefulWidget {
  final String conversationId;
  final ConversationLockProvider provider;

  const ConversationLockSettingsDialog({
    super.key,
    required this.conversationId,
    required this.provider,
  });

  @override
  State<ConversationLockSettingsDialog> createState() =>
      _ConversationLockSettingsDialogState();
}

class _ConversationLockSettingsDialogState
    extends State<ConversationLockSettingsDialog> {
  bool _canUseBiometric = false;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _checkBiometric();
  }

  Future<void> _checkBiometric() async {
    final canUse = await widget.provider.canCheckBiometrics();
    setState(() {
      _canUseBiometric = canUse;
      _isLoading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    return AlertDialog(
      title: const Text(
        'Lock Conversation',
        style: TextStyle(color: ColorConstants.primaryColor),
      ),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ListTile(
            leading: const Icon(Icons.lock, color: ColorConstants.primaryColor),
            title: const Text('Lock with PIN'),
            onTap: () async {
              Navigator.pop(context);
              _showSetPINDialog();
            },
          ),
          if (_canUseBiometric)
            ListTile(
              leading: const Icon(Icons.fingerprint,
                  color: ColorConstants.primaryColor),
              title: const Text('Lock with Biometric'),
              onTap: () async {
                Navigator.pop(context);
                final success =
                await widget.provider.setConversationBiometric(
                  conversationId: widget.conversationId,
                );
                if (success) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                        content: Text('Biometric lock enabled')),
                  );
                }
              },
            ),
          ListTile(
            leading:
            const Icon(Icons.lock_open, color: ColorConstants.primaryColor),
            title: const Text('Remove Lock'),
            onTap: () async {
              Navigator.pop(context);
              final success = await widget.provider.removeConversationLock(
                widget.conversationId,
              );
              if (success) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Lock removed')),
                );
              }
            },
          ),
        ],
      ),
    );
  }

  void _showSetPINDialog() {
    String? firstPin;

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => PINInputDialog(
        title: 'Set PIN',
        onComplete: (pin) {
          firstPin = pin;
          Navigator.pop(context);
          _showConfirmPINDialog(firstPin!);
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
        isConfirm: true,
        onComplete: (pin) async {
          Navigator.pop(context);

          if (pin == firstPin) {
            final success = await widget.provider.setConversationPIN(
              conversationId: widget.conversationId,
              pin: pin,
            );

            if (success) {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('PIN lock enabled')),
              );
            }
          } else {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('PINs do not match')),
            );
          }
        },
      ),
    );
  }
}