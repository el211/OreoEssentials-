// File: src/main/java/fr/elias/oreoEssentials/customcraft/OeCraftCommand.java
package fr.elias.oreoEssentials.customcraft;

import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.InventoryManager;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;

public final class OeCraftCommand implements CommandExecutor, TabCompleter {
    private final Plugin plugin;
    private final InventoryManager invMgr;
    private final CustomCraftingService service;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public OeCraftCommand(Plugin plugin, InventoryManager invMgr, CustomCraftingService service) {
        this.plugin = plugin;
        this.invMgr = invMgr;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // ---- Help / no args ----
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendUsage(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // ---- Reload (console OK) ----
        if (sub.equals("reload")) {
            if (!sender.hasPermission("oreo.craft")) {
                send(sender, "economy.errors.no-permission",
                        "<red>You don't have permission.</red>", Map.of());
                return true;
            }
            service.loadAllAndRegister();
            send(sender, "customcraft.messages.reloaded",
                    "%prefix% <green>Custom recipes reloaded.</green>", Map.of());
            return true;
        }

        // ---- List (console OK) ----
        if (sub.equals("list")) {
            var names = new TreeSet<>(service.allNames());
            send(sender, "customcraft.messages.list",
                    "%prefix% <gray>Loaded:</gray> <white>%count%</white> <gray>(%names%)</gray>",
                    Map.of("count", String.valueOf(names.size()), "names", String.join(", ", names)));
            return true;
        }

        // ---- Delete (console OK) ----
        if (sub.equals("delete")) {
            if (!sender.hasPermission("oreo.craft")) {
                send(sender, "economy.errors.no-permission",
                        "<red>You don't have permission.</red>", Map.of());
                return true;
            }
            if (args.length < 2) {
                sendRaw(sender, "<red>Usage:</red> /" + label + " delete <name>");
                return true;
            }
            String name = sanitize(args[1]);
            boolean ok = service.delete(name);
            if (ok) {
                send(sender, "customcraft.messages.deleted",
                        "%prefix% <green>Deleted recipe <yellow>%name%</yellow>.</green>",
                        Map.of("name", name));
            } else {
                send(sender, "customcraft.messages.invalid",
                        "%prefix% <red>Invalid recipe.</red>", Map.of());
            }
            return true;
        }

        // ---- Everything below requires a Player (GUI) ----
        if (!(sender instanceof Player p)) {
            sendRaw(sender, "<red>This subcommand requires a player.</red>");
            return true;
        }
        if (!p.hasPermission("oreo.craft")) {
            send(p, "economy.errors.no-permission",
                    "<red>You don't have permission.</red>", Map.of());
            return true;
        }

        // ---- Browse (GUI) ----
        if (sub.equals("browse")) {
            RecipeListMenu.open(p, plugin, invMgr, service);
            return true;
        }

        // ---- Craft <name> (open editor / create-or-edit) ----
        if (sub.equals("craft")) {
            if (args.length < 2) {
                sendRaw(p, "<red>Usage:</red> /" + label + " craft <name>");
                return true;
            }
            String name = sanitize(args[1]);
            CraftDesignerMenu.build(plugin, invMgr, service, name).open(p);
            return true;
        }

        sendRaw(sender, "<red>Unknown subcommand.</red> <yellow>Use</yellow> /" + label + " help");
        return true;
    }

    private static String sanitize(String raw) {
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }

    private void sendUsage(CommandSender sender, String label) {
        // MiniMessage for player; plain text fallback for console
        sendRaw(sender, "<yellow>Usage:</yellow>");
        sendRaw(sender, "<yellow>/" + label + " browse</yellow> <gray>→ open recipe browser (GUI)</gray>");
        sendRaw(sender, "<yellow>/" + label + " craft <name></yellow> <gray>→ open designer GUI</gray>");
        sendRaw(sender, "<yellow>/" + label + " list</yellow> <gray>→ list recipe names</gray>");
        sendRaw(sender, "<yellow>/" + label + " reload</yellow> <gray>→ reload recipes.yml & re-register</gray>");
        sendRaw(sender, "<yellow>/" + label + " delete <name></yellow> <gray>→ delete a recipe</gray>");
    }

    /* ---------------- Tab Completion ---------------- */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            List<String> base = List.of("browse", "craft", "list", "reload", "delete");
            String pref = args[0].toLowerCase(Locale.ROOT);
            out = base.stream().filter(s -> s.startsWith(pref)).collect(Collectors.toList());
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("craft") || sub.equals("delete")) {
                String pref = args[1].toLowerCase(Locale.ROOT);
                out = service.allNames().stream()
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pref))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }
        }

        return out;
    }

    /* ---------------- MiniMessage helpers ---------------- */

    /** Send a message using a lang key; player = full MM, console = plain text. */
    private static void send(CommandSender to, String key, String def, Map<String, String> vars) {
        if (to instanceof Player p) {
            Component comp = mm(key, def, vars, p);
            p.sendMessage(comp);
        } else {
            // console: plain fallback
            String raw = resolve(key, def, vars, null);
            String plain = PLAIN.serialize(MM.deserialize(raw));
            to.sendMessage(plain);
        }
    }

    /** Send a raw MiniMessage string (no lang key). */
    private static void sendRaw(CommandSender to, String rawMiniMsg) {
        if (to instanceof Player p) p.sendMessage(MM.deserialize(rawMiniMsg));
        else to.sendMessage(PLAIN.serialize(MM.deserialize(rawMiniMsg)));
    }

    /** Build a MiniMessage component from lang + vars (+PAPI if player). */
    private static Component mm(String key, String def, Map<String, String> vars, Player player) {
        String raw = resolve(key, def, vars, player);
        return MM.deserialize(raw);
    }

    /** Resolve lang key → text, apply %vars%, then PAPI (if player). */
    private static String resolve(String key, String def, Map<String, String> vars, Player player) {
        String raw = Lang.get(key, def);
        if (vars != null) {
            for (var e : vars.entrySet()) raw = raw.replace("%" + e.getKey() + "%", e.getValue());
        }
        if (player != null && isPapiPresent()) {
            try { raw = PlaceholderAPI.setPlaceholders(player, raw); } catch (Throwable ignored) {}
        }
        return raw;
    }

    private static boolean isPapiPresent() {
        try { return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"); }
        catch (Throwable t) { return false; }
    }
}
