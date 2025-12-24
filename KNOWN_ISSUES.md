## Known Issues

### 1. ShortcutEditorScreen double-tap bug
**Symptom:** After dismissing the Shortcut Editor screen via swipe-down gesture, the first tap on "New shortcut" button or existing shortcut cards does not respond. A second tap is required to open the editor.

**Workaround:** Use the back button or close icon to dismiss the editor instead of swiping down, OR tap twice after swipe dismissal.

**Status:** Open - requires further investigation into Compose's touch event dispatch after animated dismissal.

### 2. Full-screen app list flick scroll bug
**Symptom:** When the assistant view is expanded to full screen, first upward flicks (flings) on the app list cause the list to scroll in the opposite direction, jumping back to the top instead of scrolling down to show more apps. Slow dragging works correctly.

**Technical Details:** The fling velocity appears inverted when the sheet is at maximum height. Investigation shows the velocity is positive when it should be negative for an upward gesture. The nested scroll connection and gesture handlers were examined but the root cause could not be determined. 

**Attempts to fix included:**
- Adding tolerance checks for floating-point precision in sheet fraction comparisons
- Changing pointerInput keys to prevent gesture detector restarts
- Adding early returns in onPostScroll/onPostFling when at max height
- Replacing detectVerticalDragGestures with custom awaitEachGesture to skip gesture handling at full screen

**Workaround:** Drag/scroll slowly instead of flicking when in full-screen mode.

**Status:** Open - suspected issue with Compose's nested scroll/fling velocity calculation when combined with animated container heights.

### 3. Double gesture required after long inactivity to open assistant
**Symptom:** When opening the assistant after a long period of inactivity, users may need to perform the invocation gesture twice to bring up the assistant bottom sheet. This affects both corner swipe gestures and power button long-press.

**Suspected Cause:** The system appears to be clearing the app from memory during extended periods of non-use, causing the first gesture to wake/initialize the app while the second gesture actually invokes the assistant interface.

**Workaround:** Swipe diagonally from corners twice or press and hold the power button twice if the assistant doesn't appear on the first attempt.

**Status:** Open - likely related to Android's memory management and app lifecycle when acting as VoiceInteractionService.