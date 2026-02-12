package fr.elias.oreoEssentials.modules.scoreboard;

import java.util.List;

final class AnimatedText {
    private final List<String> frames;
    private final long frameTicks;
    private int index = 0;
    private long ticks = 0;

    AnimatedText(List<String> frames, long frameTicks) {
        this.frames = frames;
        this.frameTicks = Math.max(1, frameTicks);
    }

    String current() {
        if (frames.isEmpty()) return "";
        return frames.get(index);
    }

    void tick() {
        if (frames.size() <= 1) return;
        ticks++;
        if (ticks >= frameTicks) {
            ticks = 0;
            index = (index + 1) % frames.size();
        }
    }
}
