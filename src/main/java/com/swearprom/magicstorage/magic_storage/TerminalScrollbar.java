package com.swearprom.magicstorage.magic_storage;

import java.util.OptionalInt;

final class TerminalScrollbar {
    static final int THUMB_WIDTH = 12;
    static final int THUMB_HEIGHT = 15;
    private static final int INITIAL_REPEAT_TICKS = 5;
    private static final int REPEAT_TICKS = 3;

    enum VisualState {
        ENABLED,
        CLICKED,
        DISABLED
    }

    private boolean dragging;
    private int grabOffset;
    private int repeatDirection;
    private int repeatCountdown;
    private int optimisticPosition;
    private int observedServerPosition = Integer.MIN_VALUE;

    static int thumbTop(int trackTop, int trackHeight, int position, int maxPosition) {
        int travel = Math.max(0, trackHeight - THUMB_HEIGHT);
        if (travel == 0 || maxPosition <= 0) return trackTop;
        return trackTop + (int) ((long) travel * Math.clamp(position, 0, maxPosition) / maxPosition);
    }

    int synchronize(int serverPosition, int maxPosition) {
        int clamped = Math.clamp(serverPosition, 0, Math.max(0, maxPosition));
        if (observedServerPosition == Integer.MIN_VALUE
                || observedServerPosition != clamped) {
            observedServerPosition = clamped;
            optimisticPosition = clamped;
        }
        return Math.clamp(optimisticPosition, 0, Math.max(0, maxPosition));
    }

    int press(
            double pointerY,
            int trackTop,
            int trackHeight,
            int position,
            int maxPosition,
            int pageStep
    ) {
        optimisticPosition = Math.clamp(position, 0, Math.max(0, maxPosition));
        if (maxPosition <= 0) return 0;
        int thumbTop = thumbTop(trackTop, trackHeight, optimisticPosition, maxPosition);
        if (pointerY >= thumbTop && pointerY < thumbTop + THUMB_HEIGHT) {
            dragging = true;
            grabOffset = Math.clamp((int) pointerY - thumbTop, 0, THUMB_HEIGHT - 1);
            repeatDirection = 0;
            return optimisticPosition;
        }
        repeatDirection = pointerY < thumbTop ? -1 : 1;
        repeatCountdown = INITIAL_REPEAT_TICKS;
        return step(optimisticPosition, repeatDirection * pageStep, maxPosition);
    }

    int drag(double pointerY, int trackTop, int trackHeight, int maxPosition) {
        if (!dragging) return optimisticPosition;
        int travel = Math.max(0, trackHeight - THUMB_HEIGHT);
        if (travel == 0 || maxPosition <= 0) return 0;
        double thumbTop = Math.clamp(
                pointerY - grabOffset, trackTop, trackTop + travel);
        optimisticPosition = Math.clamp(
                (int) ((thumbTop - trackTop) * maxPosition / travel),
                0,
                maxPosition);
        return optimisticPosition;
    }

    OptionalInt tick(int maxPosition, int pageStep) {
        if (repeatDirection == 0 || --repeatCountdown > 0) return OptionalInt.empty();
        repeatCountdown = REPEAT_TICKS;
        return OptionalInt.of(step(
                optimisticPosition, repeatDirection * pageStep, maxPosition));
    }

    int step(int position, int delta, int maxPosition) {
        optimisticPosition = Math.clamp(
                (int) Math.clamp((long) position + delta, 0L, Math.max(0, maxPosition)),
                0,
                Math.max(0, maxPosition));
        return optimisticPosition;
    }

    void release() {
        dragging = false;
        repeatDirection = 0;
        repeatCountdown = 0;
    }

    boolean isDragging() {
        return dragging;
    }

    boolean isPressed() {
        return dragging || repeatDirection != 0;
    }

    VisualState visualState(int maxPosition) {
        if (maxPosition <= 0) return VisualState.DISABLED;
        return isPressed() ? VisualState.CLICKED : VisualState.ENABLED;
    }

}
