import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:audioplayers/audioplayers.dart';

class WheelPicker extends StatefulWidget {
  final int minValue;
  final int maxValue;
  final int initialValue;
  final ValueChanged<int> onChanged;
  final String label;
  final double width;

  const WheelPicker({
    super.key,
    required this.minValue,
    required this.maxValue,
    required this.initialValue,
    required this.onChanged,
    required this.label,
    this.width = 70,
  });

  @override
  State<WheelPicker> createState() => _WheelPickerState();
}

class _WheelPickerState extends State<WheelPicker> {
  late FixedExtentScrollController _controller;
  static final AudioPlayer _audioPlayer = AudioPlayer(); // Static shared player
  Timer? _soundTimer;

  @override
  void initState() {
    super.initState();
    _controller = FixedExtentScrollController(
      initialItem: widget.initialValue - widget.minValue,
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    _soundTimer?.cancel();
    super.dispose();
  }

  void _handleSelection(int index) {
    // 1. Haptic feedback is instant and doesn't lag
    HapticFeedback.selectionClick();

    // 2. Debounce sound to prevent "machine gun" effect and UI freezing
    // Only play if there hasn't been a scroll event in the last 50ms
    _soundTimer?.cancel();
    _soundTimer = Timer(const Duration(milliseconds: 40), () {
      _playSound();
    });

    widget.onChanged(widget.minValue + index);
  }

  void _playSound() {
    // Fire and forget to avoid blocking the main thread
    _audioPlayer.stop().then((_) {
      _audioPlayer.play(AssetSource('sounds/gear.mp3'), volume: 0.4);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (widget.label.isNotEmpty) ...[
          Text(
            widget.label,
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.bold,
              color: Colors.deepPurple.withOpacity(0.7),
              letterSpacing: 0.5,
            ),
          ),
          const SizedBox(height: 10),
        ],
        Container(
          height: 160,
          width: widget.width,
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(20),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.03),
                blurRadius: 10,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Stack(
            children: [
              // Selection highlight
              Center(
                child: Container(
                  height: 45,
                  width: widget.width - 10,
                  decoration: BoxDecoration(
                    color: Colors.deepPurple.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
              ),
              ListWheelScrollView.useDelegate(
                controller: _controller,
                itemExtent: 45,
                perspective: 0.008,
                diameterRatio: 1.5,
                physics: const FixedExtentScrollPhysics(),
                onSelectedItemChanged: _handleSelection,
                childDelegate: ListWheelChildBuilderDelegate(
                  builder: (context, index) {
                    return Center(
                      child: Text(
                        '${widget.minValue + index}',
                        style: const TextStyle(
                          fontSize: 22,
                          fontWeight: FontWeight.w800,
                          color: Colors.black87,
                        ),
                      ),
                    );
                  },
                  childCount: widget.maxValue - widget.minValue + 1,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
