package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.modules.tab.TabListManager;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public final class ReloadAllCommand implements OreoCommand {

    @Override public String name() { return "oereload"; }
    @Override public List<String> aliases() { return List.of("oereloadall", "oer"); }
    @Override public String permission() { return "oreo.reload"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        final OreoEssentials plugin = OreoEssentials.get();
        final long start = System.currentTimeMillis();

        int ok = 0, skip = 0;

        // 0) settings.yml
        try {
            plugin.getSettingsConfig().reload();

            fr.elias.oreoEssentials.config.CrossServerSettings cross =
                    fr.elias.oreoEssentials.config.CrossServerSettings.load(plugin);

            var f = OreoEssentials.class.getDeclaredField("crossServerSettings");
            f.setAccessible(true);
            f.set(plugin, cross);

            Lang.send(sender, "admin.reload.settings",
                    "<green>✔ Reloaded <white>settings.yml</white> <gray>(+ cross-server settings)</gray></green>");
            ok++;
        } catch (Throwable t) {
            Lang.send(sender, "admin.reload.settings-skip",
                    "<yellow>• Skipped settings.yml / cross-server: <gray>%error%</gray></yellow>",
                    Map.of("error", t.getMessage()));
            skip++;
        }
        try {
            var ar = plugin.getAutoRebootService();
            if (ar != null) {
                ar.reload();
                Lang.send(sender, "admin.reload.autoreboot",
                        "<green>✔ Reloaded <white>auto-reboot</white> <gray>(settings.yml)</gray></green>");
                ok++;
            } else {
                Lang.send(sender, "admin.reload.autoreboot-skip",
                        "<yellow>• Skipped auto-reboot (service unavailable)</yellow>");
                skip++;
            }
        } catch (Throwable t) {
            Lang.send(sender, "admin.reload.autoreboot-fail",
                    "<red>✘ Failed reloading auto-reboot: <gray>%error%</gray></red>",
                    Map.of("error", t.getMessage()));
        }

        // 1) Main config.yml
        try {
            plugin.reloadConfig();
            Lang.send(sender, "admin.reload.config",
                    "<green>✔ Reloaded <white>config.yml</white></green>");
            ok++;
        } catch (Throwable t) {
            Lang.send(sender, "admin.reload.config-fail",
                    "<red>✘ Failed reloading config.yml: <gray>%error%</gray></red>",
                    Map.of("error", t.getMessage()));
        }

        // 2) lang.yml
        try {
            Lang.init(plugin);
            Lang.send(sender, "admin.reload.lang",
                    "<green>✔ Reloaded <white>lang.yml</white></green>");
            ok++;
        } catch (Throwable t) {
            Lang.send(sender, "admin.reload.lang-skip",
                    "<yellow>• Skipped lang.yml: <gray>%error%</gray></yellow>",
                    Map.of("error", t.getMessage()));
            skip++;
        }

        // 3) Chat (chat-format.yml etc.)
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "afelius reload all");
            Lang.send(sender, "admin.reload.chat",
                    "<green>✔ Reloaded <white>chat-format.yml</white> <gray>(via /afelius reload all)</gray></green>");
            ok++;
        } catch (Throwable t) {
            Lang.send(sender, "admin.reload.chat-skip",
                    "<yellow>• Skipped chat-format.yml: <gray>%error%</gray></yellow>",
                    Map.of("error", t.getMessage()));
            skip++;
        }

        // 4) Tab list
        try {
            TabListManager tab = plugin.getTabListManager();
            if (tab != null) {
                tab.reload();
                Lang.send(sender, "admin.reload.tab",
                        "<green>✔ Reloaded <white>tab.yml</white></green>");
                ok++;
            } else {
                Lang.send(sender, "admin.reload.tab-skip",
                        "<yellow>• Skipped tab.yml (manager unavailable)</yellow>");
                skip++;
            }
        } catch (Throwable t) {
            Lang.send(sender, "admin.reload.tab-skip",
                    "<yellow>• Skipped tab.yml: <gray>%error%</gray></yellow>",
                    Map.of("error", t.getMessage()));
            skip++;
        }

        // 5) Kits
        try {
            Object km = plugin.getKitsManager();
            if (km != null) {
                if (!callNoArgs(km, "reload") && !callNoArgs(km, "load")) {
                    throw new IllegalStateException("no reload/load available");
                }
                Lang.send(sender, "admin.reload.kits",
                        "<green>✔ Reloaded <white>kits.yml</white></green>");
                ok++;
            } else {
                Lang.send(sender, "admin.reload.kits-skip",
                        "<yellow>• Skipped kits.yml (manager unavailable)</yellow>");
                skip++;
            }
        } catch (Throwable t) {
            Lang.send(sender, "admin.reload.kits-skip",
                    "<yellow>• Skipped kits.yml: <gray>%error%</gray></yellow>",
                    Map.of("error", t.getMessage()));
            skip++;
        }

        // 6) RTP
        try {
            var rtpCfg = plugin.getRtpConfig();
            if (rtpCfg != null) {
                rtpCfg.reload();
                Lang.send(sender, "admin.reload.rtp",
                        "<green>✔ Reloaded <white>rtp.yml</white></green>");
                ok++;
            } else {
                Lang.send(sender, "admin.reload.rtp-skip",
                        "<yellow>• Skipped rtp.yml (service unavailable)</yellow>");
                skip++;
            }
        } catch (Throwable t) {
            Lang.send(sender, "admin.reload.rtp-skip",
                    "<yellow>• Skipped rtp.yml: <gray>%error%</gray></yellow>",
                    Map.of("error", t.getMessage()));
            skip++;
        }

        // 7) EnderChest
        try {
            var ecService = plugin.getEnderChestService();
            if (ecService != null && (callNoArgs(ecService, "reload") || callNoArgs(ecService, "refresh"))) {
                Lang.send(sender, "admin.reload.enderchest",
                        "<green>✔ Reloaded <white>enderchest.yml</white></green>");
                ok++;
            } else {
                Lang.send(sender, "admin.reload.enderchest-skip",
                        "<yellow>• Skipped enderchest.yml (no reload hook)</yellow>");
                skip++;
            }
        } catch (Throwable ignored) {
            Lang.send(sender, "admin.reload.enderchest-skip",
                    "<yellow>• Skipped enderchest.yml (no reload hook)</yellow>");
            skip++;
        }

        // 8) BossBar
        try {
            var bb = plugin.getBossBarService();
            if (bb != null) {
                bb.reload();
                Lang.send(sender, "admin.reload.bossbar",
                        "<green>✔ Reloaded <white>bossbar</white></green>");
                ok++;
            } else {
                Lang.send(sender, "admin.reload.bossbar-skip",
                        "<yellow>• Skipped bossbar (service unavailable)</yellow>");
                skip++;
            }
        } catch (Throwable t) {
            Lang.send(sender, "admin.reload.bossbar-fail",
                    "<red>✘ Failed reloading bossbar: <gray>%error%</gray></red>",
                    Map.of("error", t.getMessage()));
        }

        // 9) Scoreboard
        try {
            if (plugin.getScoreboardService() != null) {
                plugin.getScoreboardService().reload();
                Lang.send(sender, "admin.reload.scoreboard",
                        "<green>✔ Reloaded <white>scoreboard</white> <gray>(config + live refresh)</gray></green>");
                ok++;
            } else {
                Lang.send(sender, "admin.reload.scoreboard-skip",
                        "<yellow>• Skipped scoreboard (service unavailable)</yellow>");
                skip++;
            }
        } catch (Throwable t) {
            Lang.send(sender, "admin.reload.scoreboard-fail",
                    "<red>✘ Failed reloading scoreboard: <gray>%error%</gray></red>",
                    Map.of("error", t.getMessage()));
        }

        // 10) Discord integration
        try {
            var dm = plugin.getDiscordMod();
            if (dm != null && callNoArgs(dm, "reload")) {
                Lang.send(sender, "admin.reload.discord",
                        "<green>✔ Reloaded <white>discord integration</white></green>");
                ok++;
            } else {
                Lang.send(sender, "admin.reload.discord-skip",
                        "<yellow>• Skipped discord integration (no reload hook)</yellow>");
                skip++;
            }
        } catch (Throwable ignored) {
            Lang.send(sender, "admin.reload.discord-skip",
                    "<yellow>• Skipped discord integration (no reload hook)</yellow>");
            skip++;
        }

        // 11) Health bars
        try {
            var core = OreoEssentials.get();

            fr.elias.ultimateChristmas.UltimateChristmas xmasHook = null;
            try {
                org.bukkit.plugin.Plugin maybe = core.getServer().getPluginManager().getPlugin("UltimateChristmas");
                if (maybe instanceof fr.elias.ultimateChristmas.UltimateChristmas uc && maybe.isEnabled()) {
                    xmasHook = uc;
                }
            } catch (Throwable ignored) {}

            var hbl = new fr.elias.oreoEssentials.modules.mobs.HealthBarListener(core, xmasHook);

            if (hbl.isEnabled()) {
                Bukkit.getPluginManager().registerEvents(hbl, core);

                var f = OreoEssentials.class.getDeclaredField("healthBarListener");
                f.setAccessible(true);
                f.set(core, hbl);

                Lang.send(sender, "admin.reload.healthbars",
                        "<green>✔ Reloaded <white>mob health bars</white></green>");
            } else {
                Lang.send(sender, "admin.reload.healthbars-disabled",
                        "<yellow>• Mob health bars disabled by config</yellow>");

                var f = OreoEssentials.class.getDeclaredField("healthBarListener");
                f.setAccessible(true);
                f.set(core, null);
            }

        } catch (Throwable t) {
            Lang.send(sender, "admin.reload.healthbars-skip",
                    "<yellow>• Skipped mob health bars: <gray>%error%</gray></yellow>",
                    Map.of("error", t.getMessage()));
        }

        // 12) PlayerVaults
        try {
            var pv = plugin.getPlayervaultsService();
            if (pv != null) {
                pv.reload();
                Lang.send(sender, "admin.reload.playervaults",
                        "<green>✔ Reloaded <white>playervaults</white> <gray>(%storage%)</gray></green>",
                        Map.of("storage", pvStorageName(pv)));
                ok++;
            } else {
                Lang.send(sender, "admin.reload.playervaults-skip",
                        "<yellow>• Skipped playervaults (service unavailable)</yellow>");
                skip++;
            }
        } catch (Throwable t) {
            Lang.send(sender, "admin.reload.playervaults-fail",
                    "<red>✘ Failed reloading playervaults: <gray>%error%</gray></red>",
                    Map.of("error", t.getMessage()));
        }
        // 13) Nametags
        try {
            var nametagMgr = plugin.getNametagManager();
            if (nametagMgr != null) {
                nametagMgr.reload(plugin.getSettingsConfig().raw());  // ← FIXED
                Lang.send(sender, "admin.reload.nametags",
                        "<green>✔ Reloaded <white>nametags</white> <gray>(config + live refresh)</gray></green>");
                ok++;
            } else {
                Lang.send(sender, "admin.reload.nametags-skip",
                        "<yellow>• Skipped nametags (service unavailable)</yellow>");
                skip++;
            }
        } catch (Throwable t) {
            Lang.send(sender, "admin.reload.nametags-fail",
                    "<red>✘ Failed reloading nametags: <gray>%error%</gray></red>",
                    Map.of("error", t.getMessage()));
        }
        try {
            for (var p : Bukkit.getOnlinePlayers()) {
                try {
                    p.updateCommands(); // Paper: refresh brigadier + command suggestions
                } catch (Throwable ignored) {}
            }
            Lang.send(sender, "admin.reload.commands-refresh",
                    "<green>✔ Refreshed <white>command suggestions</white> <gray>(updateCommands)</gray></green>");
        } catch (Throwable ignored) {}

        // Summary
        long took = System.currentTimeMillis() - start;
        Lang.send(sender, "admin.reload.summary",
                "<gray>Reload complete: <green>%ok% OK</green>, <yellow>%skip% skipped</yellow>. (<white>%ms% ms</white>)</gray>",
                Map.of("ok", String.valueOf(ok), "skip", String.valueOf(skip), "ms", String.valueOf(took)));
        Lang.send(sender, "admin.reload.note",
                "<dark_gray>(Some modules don't support hot reload yet; a server restart may be required.)</dark_gray>");

        return true;
    }

    private static boolean callNoArgs(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            m.setAccessible(true);
            m.invoke(target);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String pvStorageName(fr.elias.oreoEssentials.modules.playervaults.PlayerVaultsService svc) {
        try {
            var f = svc.getClass().getDeclaredField("storage");
            f.setAccessible(true);
            Object st = f.get(svc);
            if (st == null) return "disabled";
            String n = st.getClass().getSimpleName().toLowerCase();
            if (n.contains("mongo")) return "mongodb";
            if (n.contains("yaml"))  return "yaml";
            return n;
        } catch (Throwable ignored) {
            return "ok";
        }
    }
}