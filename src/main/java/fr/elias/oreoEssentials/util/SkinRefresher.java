package fr.elias.oreoEssentials.util;

import org.bukkit.entity.Player;

public interface SkinRefresher {
    void refresh(Player player);

    final class Holder {
        private static SkinRefresher IMPL = new SkinRefresherFallback();

        private Holder() {}

        public static void set(SkinRefresher impl) {
            if (impl != null) IMPL = impl;
        }
        public static SkinRefresher get() { return IMPL; }
        public static void refresh(Player p) { IMPL.refresh(p); }
    }
}
