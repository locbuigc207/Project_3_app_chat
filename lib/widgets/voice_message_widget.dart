import 'package:flutter/material.dart';
import 'package:flutter_chat_demo/constants/constants.dart';
import 'package:flutter_chat_demo/providers/providers.dart';
import 'package:flutter_sound/flutter_sound.dart';

class VoiceMessageWidget extends StatefulWidget {
  final String voiceUrl;
  final bool isMyMessage;
  final VoiceMessageProvider voiceProvider;

  const VoiceMessageWidget({
    super.key,
    required this.voiceUrl,
    required this.isMyMessage,
    required this.voiceProvider,
  });

  @override
  State<VoiceMessageWidget> createState() => _VoiceMessageWidgetState();
}

class _VoiceMessageWidgetState extends State<VoiceMessageWidget> {
  bool _isPlaying = false;
  double _currentPosition = 0;
  double _totalDuration = 1;

  @override
  void initState() {
    super.initState();
    _setupPlayer();
  }

  Future<void> _setupPlayer() async {
    await widget.voiceProvider.initPlayer();

    widget.voiceProvider.playbackStream?.listen((event) {
      if (mounted) {
        setState(() {
          _currentPosition = event.position.inMilliseconds.toDouble();
          _totalDuration = event.duration.inMilliseconds.toDouble();

          if (_currentPosition >= _totalDuration && _totalDuration > 0) {
            _isPlaying = false;
          }
        });
      }
    });
  }

  Future<void> _togglePlayback() async {
    if (_isPlaying) {
      await widget.voiceProvider.pausePlayback();
    } else {
      await widget.voiceProvider.playVoiceMessage(widget.voiceUrl);
    }

    setState(() {
      _isPlaying = !_isPlaying;
    });
  }

  String _formatDuration(double milliseconds) {
    final duration = Duration(milliseconds: milliseconds.toInt());
    final minutes = duration.inMinutes;
    final seconds = duration.inSeconds % 60;
    return '$minutes:${seconds.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: widget.isMyMessage
            ? ColorConstants.primaryColor
            : ColorConstants.greyColor2,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Play/Pause button
          IconButton(
            icon: Icon(
              _isPlaying ? Icons.pause : Icons.play_arrow,
              color: widget.isMyMessage ? Colors.white : Colors.black87,
            ),
            onPressed: _togglePlayback,
            padding: EdgeInsets.zero,
            constraints: BoxConstraints(),
          ),

          const SizedBox(width: 8),

          // Waveform/Progress
          Container(
            width: 150,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Progress bar
                SliderTheme(
                  data: SliderThemeData(
                    trackHeight: 2,
                    thumbShape: RoundSliderThumbShape(enabledThumbRadius: 4),
                    overlayShape: RoundSliderOverlayShape(overlayRadius: 8),
                  ),
                  child: Slider(
                    value: _currentPosition.clamp(0, _totalDuration),
                    max: _totalDuration,
                    activeColor: widget.isMyMessage
                        ? Colors.white
                        : ColorConstants.primaryColor,
                    inactiveColor: widget.isMyMessage
                        ? Colors.white38
                        : ColorConstants.greyColor,
                    onChanged: (value) {
                      // Seek functionality can be added here
                    },
                  ),
                ),

                // Duration
                Text(
                  '${_formatDuration(_currentPosition)} / ${_formatDuration(_totalDuration)}',
                  style: TextStyle(
                    fontSize: 11,
                    color: widget.isMyMessage
                        ? Colors.white70
                        : ColorConstants.greyColor,
                  ),
                ),
              ],
            ),
          ),

          const SizedBox(width: 4),

          // Voice icon
          Icon(
            Icons.mic,
            size: 16,
            color:
                widget.isMyMessage ? Colors.white70 : ColorConstants.greyColor,
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    if (_isPlaying) {
      widget.voiceProvider.stopPlayback();
    }
    super.dispose();
  }
}
