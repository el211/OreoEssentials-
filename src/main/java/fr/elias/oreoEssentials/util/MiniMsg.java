package fr.elias.oreoEssentials.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class MiniMsg {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static String toLegacy(String input) {
        Component c = MM.deserialize(input);
        return LegacyComponentSerializer.legacySection().serialize(c);
    }
}
