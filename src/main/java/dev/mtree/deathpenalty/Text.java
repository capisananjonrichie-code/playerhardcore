package dev.mtree.deathpenalty;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Tiny helper: turns "&6text" into an Adventure Component. */
final class Text {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {}

    static Component color(String s) {
        return LEGACY.deserialize(s == null ? "" : s);
    }

    /** 300 -> "5 minutes", 90 -> "90 seconds". */
    static String duration(int seconds) {
        if (seconds % 60 == 0) {
            int m = seconds / 60;
            return m + (m == 1 ? " minute" : " minutes");
        }
        return seconds + " seconds";
    }
}
