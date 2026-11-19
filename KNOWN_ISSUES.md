## Known Issues

### 1. Full-screen app list flick scroll bug
**Symptom:** When the assistant view is expanded to full screen, first upward flicks (flings) on the app list cause the list to scroll in the opposite direction, jumping back to the top instead of scrolling down to show more apps. Slow dragging works correctly.

**Technical Details:** The fling velocity appears inverted when the sheet is at maximum height. Investigation shows the velocity is positive when it should be negative for an upward gesture. The nested scroll connection and gesture handlers were examined but the root cause could not be determined. 

**Attempts to fix included:**
- Adding tolerance checks for floating-point precision in sheet fraction comparisons
- Changing pointerInput keys to prevent gesture detector restarts
- Adding early returns in onPostScroll/onPostFling when at max height
- Replacing detectVerticalDragGestures with custom awaitEachGesture to skip gesture handling at full screen

**Ruled out:** Scripted `adb shell input swipe` flicks (expand-then-flick, flick while the sheet is still springing, repeated flicks at full screen) all flung in the right direction, and could not reproduce it by hand either. The grid's fling velocity matched a velocity tracker on the non-moving parent `Box` every time, so the grid measuring velocity in its own (moving) coordinates is not the cause. Very short adb swipes report 0 velocity in both trackers; that is an adb sampling artifact, not this bug.

**Workaround:** Drag/scroll slowly instead of flicking when in full-screen mode.

**Status:** Open - not reproducible at the moment; needs a reliable repro before a fix.
