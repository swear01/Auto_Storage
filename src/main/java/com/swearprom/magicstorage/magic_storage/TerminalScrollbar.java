package com.swearprom.magicstorage.magic_storage;

final class TerminalScrollbar {
    static final int THUMB_WIDTH = 12;
    static final int THUMB_HEIGHT = 15;

    private TerminalScrollbar() {
    }

    static int thumbTop(int trackTop, int trackHeight, int position, int maxPosition) {
        int travel = Math.max(0, trackHeight - THUMB_HEIGHT);
        if (travel == 0 || maxPosition <= 0) return trackTop;
        return trackTop + (int) ((long) travel * Math.clamp(position, 0, maxPosition) / maxPosition);
    }

    static int positionForPointer(
            double pointerY,
            double grabOffset,
            int trackTop,
            int trackHeight,
            int maxPosition
    ) {
        int travel = Math.max(0, trackHeight - THUMB_HEIGHT);
        if (travel == 0 || maxPosition <= 0) return 0;
        double thumbTop = Math.clamp(pointerY - grabOffset, trackTop, trackTop + travel);
        return Math.clamp(
                (int) ((thumbTop - trackTop) * maxPosition / travel),
                0,
                maxPosition);
    }

    static int pageTarget(
            double pointerY,
            int thumbTop,
            int position,
            int pageSize,
            int maxPosition
    ) {
        if (pointerY < thumbTop) return Math.max(0, position - pageSize);
        if (pointerY >= thumbTop + THUMB_HEIGHT) {
            return Math.min(maxPosition, position + pageSize);
        }
        return position;
    }
}
