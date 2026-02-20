package fr.elias.oreoEssentials.modules.holograms;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public final class OreoHologramCommand implements TabExecutor {
    private final OreoHolograms api;

    private static volatile List<String> CACHED_VANILLA_BLOCK_KEYS;  // minecraft:stone
    private static volatile List<String> CACHED_VANILLA_BLOCK_ENUM;  // STONE
    private static volatile long VANILLA_CACHE_STAMP = 0L;

    private static volatile List<String> CACHED_IA_BLOCK_KEYS;       // itemadder:custom_block
    private static volatile long IA_CACHE_STAMP = 0L;

    public OreoHologramCommand(OreoHolograms api) { this.api = api; }

    private static boolean admin(CommandSender s) {
        if (s.hasPermission("OreoHolograms.admin") || s.hasPermission("oreo.holograms.admin")) return true;
        s.sendMessage("§cYou need OreoHolograms.admin to use this.");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!admin(s)) return true;
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) return help(s);

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "version" -> { s.sendMessage("§aOreoHolograms v1.0 (Paper/Folia Display Entities)"); return true; }
            case "list"    -> { list(s); return true; }
            case "nearby"  -> { if (!(s instanceof Player p)) { s.sendMessage("§cPlayer only."); return true; } nearby(p, a); return true; }
            case "create"  -> { if (!(s instanceof Player p)) { s.sendMessage("§cPlayer only."); return true; } create(p, a); return true; }
            case "remove"  -> { remove(s, a); return true; }
            case "copy"    -> { copy(s, a); return true; }
            case "info"    -> { info(s, a); return true; }
            case "edit"    -> { edit(s, a); return true; }
            default        -> { s.sendMessage("§cUnknown. Use /ohologram help"); return true; }
        }
    }

    private boolean help(CommandSender s) {
        s.sendMessage("§e/ohologram help §7- this help");
        s.sendMessage("§e/ohologram list §7- list all holograms");
        s.sendMessage("§e/ohologram nearby <range> §7- list nearby");
        s.sendMessage("§e/ohologram create <text|item|block> <name> [fixed|center|vertical|horizontal]");
        s.sendMessage("§e/ohologram remove <name>");
        s.sendMessage("§e/ohologram copy <name> <newName>");
        s.sendMessage("§e/ohologram info <name>");
        s.sendMessage("§e/ohologram edit <name> <subcmd...>");
        s.sendMessage("§7Subcommands: moveHere | moveTo x y z [yaw] [pitch] | rotate deg | rotatepitch deg | translate dx dy dz |");
        s.sendMessage("§7 scale f | billboard center|fixed|vertical|horizontal | shadowStrength n | shadowRadius f |");
        s.sendMessage("§7 brightness block sky | visibilityDistance d | visibility ALL|MANUAL|PERMISSION_NEEDED |");
        s.sendMessage("§7Text: setLine i text..., addLine text..., removeLine i, insertBefore i text..., insertAfter i text...");
        s.sendMessage("§7 background COLOR|#RRGGBB|TRANSPARENT, textShadow true|false, textAlignment center|left|right, updateTextInterval <ticks|5s|2m>");
        s.sendMessage("§7Item: item (from hand)");
        s.sendMessage("§7Block: block <material>  §8(tab-complete supports STONE, minecraft:stone and itemadder:* IDs)");
        return true;
    }

    private void list(CommandSender s) {
        s.sendMessage("§eHolograms:");
        for (OreoHologram h : api.all()) {
            var l = h.currentLocation();
            s.sendMessage(" §7- §f" + h.getName() + "§8 [" + h.getType() + "] §7@ §f" +
                    (l == null ? "?" : (l.getWorld().getName()+" "+l.getBlockX()+","+l.getBlockY()+","+l.getBlockZ())));
        }
    }

    private void nearby(Player p, String[] a) {
        double r = (a.length >= 2 ? parseDouble(a[1], 16.0) : 16.0);
        var pl = p.getLocation();
        var list = api.all().stream().filter(h -> {
            var l = h.currentLocation();
            return l != null && l.getWorld().equals(pl.getWorld()) && l.distance(pl) <= r;
        }).collect(Collectors.toList());
        p.sendMessage("§eNearby ("+r+" blocks):");
        for (var h : list) p.sendMessage(" - §f"+h.getName()+"§7 ("+h.getType()+")");
    }

    private void create(Player p, String[] a) {
        if (a.length < 3) {
            p.sendMessage("§cUsage: /ohologram create <text|item|block> <name> [fixed|center|vertical|horizontal]");
            return;
        }

        OreoHologramType type;
        try { type = OreoHologramType.valueOf(a[1].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { p.sendMessage("§cType must be text, item, or block."); return; }

        var name = a[2];
        var loc  = OreoHologramLocation.of(p.getLocation());

        OreoHologram h = api.create(type, name, loc);

        h.setBillboard(OreoHologramBillboard.CENTER);

        if (type == OreoHologramType.ITEM && p.getInventory().getItemInMainHand() != null) {
            var hand = p.getInventory().getItemInMainHand();
            if (hand.getType() != Material.AIR) {
                ((ItemOreoHologram) h).setItem(hand);
            }
        }

        if (a.length >= 4) {
            var bb = OreoHologramBillboard.from(a[3]);
            h.setBillboard(bb);
            if (bb == OreoHologramBillboard.FIXED) faceOnceToward(h, p); // lock toward creator once
        }

        api.save();

        p.sendMessage("§aCreated hologram §f" + name + "§a (" + type + ").");
        p.sendMessage("§7Tip: §f/ohologram edit " + name +
                " setLine 1 &fWelcome to &bYOUR LINE HERE!");
        p.sendMessage("§7Tip: §f/ohologram edit " + name +
                " addLine &7Enjoy your stay!");

    }

    private void remove(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage("§cUsage: /ohologram remove <name>"); return; }
        api.remove(a[1]); s.sendMessage("§aRemoved §f"+a[1]);
    }

    private void copy(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage("§cUsage: /ohologram copy <name> <newName>"); return; }
        api.copy(a[1], a[2]); s.sendMessage("§aCopied §f"+a[1]+"§a -> §f"+a[2]);
    }

    private void info(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage("§cUsage: /ohologram info <name>"); return; }
        var h = api.get(a[1]); if (h == null) { s.sendMessage("§cNot found."); return; }
        var d = h.toData();
        var l = h.currentLocation();
        s.sendMessage("§e"+d.name+" §7["+d.type+"] @ "+(l==null?"?":l.getWorld().getName()+" "+l.getBlockX()+","+l.getBlockY()+","+l.getBlockZ()));
        s.sendMessage("§7scale="+d.scale+" billboard="+d.billboard+" visDist="+d.visibilityDistance+" vis="+d.visibility);
        if (d.type == OreoHologramType.TEXT) s.sendMessage("§7lines="+d.lines.size()+" bg="+d.backgroundColor+" align="+d.textAlign+" shadow="+d.textShadow);
    }

    private void edit(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage("§cUsage: /ohologram edit <name> <subcmd...>"); return; }
        var h = api.get(a[1]); if (h == null) { s.sendMessage("§cNot found."); return; }
        var sub = a[2].toLowerCase(Locale.ROOT);

        // location transforms
        if ((sub.equals("movehere") || sub.equals("position")) && s instanceof Player p) {
            h.moveTo(p.getLocation()); api.save(); s.sendMessage("§aMoved."); return;
        }
        if (sub.equals("moveto")) {
            if (a.length < 6) { s.sendMessage("§cUsage: moveto x y z [yaw] [pitch]"); return; }
            Location base = h.currentLocation();
            if (base == null && s instanceof Player p) base = p.getLocation();
            if (base == null) { s.sendMessage("§cWorld not loaded."); return; }
            double x = parseDouble(a[3], base.getX());
            double y = parseDouble(a[4], base.getY());
            double z = parseDouble(a[5], base.getZ());
            float yaw   = (a.length >= 7) ? parseFloat(a[6], base.getYaw())   : base.getYaw();
            float pitch = (a.length >= 8) ? parseFloat(a[7], base.getPitch()) : base.getPitch();
            Location target = new Location(base.getWorld(), x, y, z, yaw, pitch);
            h.moveTo(target); api.save(); s.sendMessage("§aTeleported hologram."); return;
        }

        if (sub.equals("translate")) {
            if (a.length < 6) { s.sendMessage("§cUsage: translate dx dy dz"); return; }
            h.translate(parseDouble(a[3], 0), parseDouble(a[4], 0), parseDouble(a[5], 0));
            api.save(); s.sendMessage("§aTranslated."); return;
        }
        if (sub.equals("rotate")) {
            if (a.length < 4) { s.sendMessage("§cUsage: rotate degrees"); return; }
            h.rotateYaw(parseFloat(a[3], 0f)); api.save(); s.sendMessage("§aRotated yaw."); return;
        }
        if (sub.equals("rotatepitch")) {
            if (a.length < 4) { s.sendMessage("§cUsage: rotatepitch degrees"); return; }
            h.rotatePitch(parseFloat(a[3], 0f)); api.save(); s.sendMessage("§aRotated pitch."); return;
        }

        // common props
        if (sub.equals("scale")) { h.setScale(parseDouble(a[3], 1.0)); api.save(); s.sendMessage("§aScale set."); return; }
        if (sub.equals("billboard")) {
            var bb = OreoHologramBillboard.from(a.length >= 4 ? a[3] : "center");
            h.setBillboard(bb);
            // rotations cleared inside commonDisplayTweaks() when billboard ≠ FIXED
            if (bb == OreoHologramBillboard.FIXED && s instanceof Player pFix) faceOnceToward(h, pFix);
            api.save(); s.sendMessage("§aBillboard set."); return;
        }

        if (sub.equals("shadowstrength")) { h.setShadow(safeParseInt(a, 3, 0), (float)h.toData().shadowRadius); api.save(); s.sendMessage("§aShadow strength set."); return; }
        if (sub.equals("shadowradius"))   { h.setShadow(h.toData().shadowStrength, parseFloat(a[3], 0f)); api.save(); s.sendMessage("§aShadow radius set."); return; }
        if (sub.equals("brightness")) {
            if (a.length < 5) { s.sendMessage("§cUsage: brightness <block 0-15> <sky 0-15>"); return; }
            h.setBrightness(safeParseInt(a, 3, 0), safeParseInt(a, 4, 0)); api.save(); s.sendMessage("§aBrightness set."); return;
        }
        if (sub.equals("visibilitydistance")) { h.setVisibilityDistance(parseDouble(a[3], -1)); api.save(); s.sendMessage("§aView range set."); return; }
        if (sub.equals("visibility")) {
            var v = OreoHologramVisibility.valueOf(a[3].toUpperCase(Locale.ROOT));
            h.setVisibilityMode(v); api.save(); s.sendMessage("§aVisibility mode: "+v); return;
        }

        // permission / manual
        if (sub.equals("viewpermission")) { h.setViewPermission(a.length>=4 ? a[3] : ""); api.save(); s.sendMessage("§aView permission set."); return; }
        if (sub.equals("manualviewers")) {
            var names = Arrays.asList(Arrays.copyOfRange(a,3,a.length));
            h.setManualViewers(names); api.save(); s.sendMessage("§aManual viewers updated ("+names.size()+")."); return;
        }

        // TEXT
        if (h.getType() == OreoHologramType.TEXT) {
            TextOreoHologram t = (TextOreoHologram) h;
            switch (sub) {
                case "setline" -> {
                    if (a.length < 5) { s.sendMessage("§cUsage: setLine <line> <text...>"); return; }
                    int line = safeParseInt(a, 3, 1);
                    String text = String.join(" ", Arrays.copyOfRange(a, 4, a.length));
                    t.setLine(line, colorize(text)); api.save(); s.sendMessage("§aLine set.");
                }
                case "addline" -> {
                    if (a.length < 4) { s.sendMessage("§cUsage: addLine <text...>"); return; }
                    String text = String.join(" ", Arrays.copyOfRange(a, 3, a.length));
                    t.addLine(colorize(text)); api.save(); s.sendMessage("§aLine added.");
                }
                case "removeline" -> {
                    if (a.length < 4) { s.sendMessage("§cUsage: removeLine <line>"); return; }
                    t.removeLine(safeParseInt(a, 3, 1)); api.save(); s.sendMessage("§aLine removed.");
                }
                case "insertbefore" -> {
                    if (a.length < 5) { s.sendMessage("§cUsage: insertBefore <line> <text...>"); return; }
                    int line = safeParseInt(a, 3, 1);
                    String text = String.join(" ", Arrays.copyOfRange(a, 4, a.length));
                    t.insertBefore(line, colorize(text)); api.save(); s.sendMessage("§aInserted.");
                }
                case "insertafter" -> {
                    if (a.length < 5) { s.sendMessage("§cUsage: insertAfter <line> <text...>"); return; }
                    int line = safeParseInt(a, 3, 1);
                    String text = String.join(" ", Arrays.copyOfRange(a, 4, a.length));
                    t.insertAfter(line, colorize(text)); api.save(); s.sendMessage("§aInserted.");
                }
                case "background" -> { t.setBackground(a[3]); api.save(); s.sendMessage("§aBackground set."); }
                case "textshadow" -> { t.setTextShadow(Boolean.parseBoolean(a[3])); api.save(); s.sendMessage("§aText shadow set."); }
                case "textalignment" -> { t.setAlignment(a[3]); api.save(); s.sendMessage("§aText alignment set."); }
                case "updatetextinterval" -> {
                    if (a.length < 4) { s.sendMessage("§cUsage: updateTextInterval <ticks|Xs|Xm>"); return; }
                    t.setUpdateIntervalTicks(parseInterval(a[3])); api.save(); s.sendMessage("§aUpdate interval set.");
                }
                default -> s.sendMessage("§cUnknown text edit subcmd.");
            }
            return;
        }

        // ITEM
        if (h.getType() == OreoHologramType.ITEM) {
            ItemOreoHologram ih = (ItemOreoHologram) h;
            if (sub.equals("item") && s instanceof Player p) {
                ItemStack inHand = p.getInventory().getItemInMainHand();
                if (inHand == null || inHand.getType() == Material.AIR) { s.sendMessage("§cHold an item in hand."); return; }
                ih.setItem(inHand); api.save(); s.sendMessage("§aItem set from hand.");
                return;
            }
            s.sendMessage("§cUnknown item edit subcmd."); return;
        }

        // BLOCK
        if (h.getType() == OreoHologramType.BLOCK) {
            BlockOreoHologram bh = (BlockOreoHologram) h;
            if (sub.equals("block")) {
                if (a.length < 4) { s.sendMessage("§cUsage: block <material>"); return; }
                bh.setBlockType(a[3]); api.save(); s.sendMessage("§aBlock set.");
                return;
            }
            s.sendMessage("§cUnknown block edit subcmd."); return;
        }

        s.sendMessage("§cNothing matched.");
    }

    private static long parseInterval(String s) {
        if (s.endsWith("s")) return safeParseLong(s.substring(0, s.length()-1), 1) * 20L;
        if (s.endsWith("m")) return safeParseLong(s.substring(0, s.length()-1), 1) * 1200L;
        return safeParseLong(s, 20L); // ticks default
    }

    private static String colorize(String s) {
        // If user typed MiniMessage, keep it as-is
        if (s.contains("<") && s.contains(">")) return s;
        // Otherwise support legacy &
        return s.replace("&", "§");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        if (!s.hasPermission("OreoHolograms.admin") && !s.hasPermission("oreo.holograms.admin")) return List.of();

        if (a.length == 1) {
            return starts(List.of("help","version","list","nearby","create","remove","copy","info","edit"), a[0]);
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("nearby")) {
            return starts(List.of("8","16","32","64","128"), a[1]);
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("create")) {
            return starts(List.of("text","item","block"), a[1]);
        }
        if (a.length == 3 && a[0].equalsIgnoreCase("create")) {
            return List.of(); // name
        }
        if (a.length == 4 && a[0].equalsIgnoreCase("create")) {
            return starts(List.of("fixed","center","vertical","horizontal"), a[3]);
        }
        if (a.length == 2 && Set.of("remove","copy","info","edit").contains(a[0].toLowerCase(Locale.ROOT))) {
            return starts(api.all().stream().map(OreoHologram::getName).collect(Collectors.toList()), a[1]);
        }
        if (a.length == 3 && a[0].equalsIgnoreCase("edit")) {
            var h = api.get(a[1]);
            if (h == null) return List.of();

            List<String> subs = new ArrayList<>(List.of(
                    "moveHere","moveTo","translate","rotate","rotatePitch",
                    "scale","billboard","shadowStrength","shadowRadius",
                    "brightness","visibilityDistance","visibility",
                    "viewPermission","manualViewers",
                    "linkWithNpc","unlinkWithNpc"
            ));
            if (h.getType() == OreoHologramType.TEXT) {
                subs.addAll(List.of(
                        "setLine","addLine","removeLine","insertBefore","insertAfter",
                        "background","textShadow","textAlignment","updateTextInterval"
                ));
            } else if (h.getType() == OreoHologramType.ITEM) {
                subs.add("item");
            } else if (h.getType() == OreoHologramType.BLOCK) {
                subs.add("block");
            }
            return starts(subs, a[2]);
        }

        // ---- Block material completion (vanilla + ItemsAdder) ----
        if (a.length == 4 && a[0].equalsIgnoreCase("edit") && a[2].equalsIgnoreCase("block")) {
            String pref = a[3] == null ? "" : a[3].toLowerCase(Locale.ROOT);

            List<String> out = new ArrayList<>(512);
            out.addAll(getAllVanillaBlockEnum()); // STONE
            out.addAll(getAllVanillaBlockKeys()); // minecraft:stone
            out.addAll(getItemsAdderBlockKeys()); // itemadder:*

            return out.stream()
                    .filter(x -> x.toLowerCase(Locale.ROOT).startsWith(pref))
                    .limit(300)
                    .collect(Collectors.toList());
        }
        // ---- Text line-number completion (setLine/removeLine/insertBefore/insertAfter) ----
        if (a.length == 4 && a[0].equalsIgnoreCase("edit")) {
            var h = api.get(a[1]);
            if (h != null && h.getType() == OreoHologramType.TEXT) {
                String sub = a[2].toLowerCase(Locale.ROOT);
                if (sub.equals("setline") || sub.equals("removeline") || sub.equals("insertbefore") || sub.equals("insertafter")) {
                    int lines = Math.max(1, ((TextOreoHologram) h).toData().lines.size());
                    List<String> nums = new ArrayList<>(lines + 2);
                    for (int i = 1; i <= lines; i++) nums.add(String.valueOf(i));
                    nums.add(String.valueOf(lines + 1)); // allow "next line"
                    return starts(nums, a[3]);
                }
            }
        }


        if (a.length == 4 && a[0].equalsIgnoreCase("edit")) {
            switch (a[2].toLowerCase(Locale.ROOT)) {
                case "billboard":        return starts(List.of("center","fixed","vertical","horizontal"), a[3]);
                case "visibility":       return starts(List.of("ALL","MANUAL","PERMISSION_NEEDED"), a[3]);
                case "textalignment":    return starts(List.of("left","center","right"), a[3]);
                case "textshadow":       return starts(List.of("true","false"), a[3]);
                case "background":       return starts(List.of("TRANSPARENT","#FFFFFF","#000000","RED","GREEN","BLUE","AQUA","YELLOW"), a[3]);
                default:                 return List.of();
            }
        }
        return List.of();
    }

    private static List<String> starts(Collection<String> base, String pref) {
        String p = pref == null ? "" : pref.toLowerCase(Locale.ROOT);
        return base.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }

    /** Face the hologram toward 'viewer' once, then keep its yaw/pitch. */
    private static void faceOnceToward(OreoHologram h, Player viewer) {
        Location from = h.currentLocation();
        if (from == null) return;

        Location to = viewer.getEyeLocation();

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();

        double distXZ = Math.max(0.0001, Math.sqrt(dx*dx + dz*dz));
        float yaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, distXZ)));

        Location aimed = new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), yaw, pitch);
        h.moveTo(aimed);
    }

    /* ---------- tab-complete sources ---------- */

    /** Cached list of `minecraft:<block_id>` for every Material that is a block. */
    private static List<String> getAllVanillaBlockKeys() {
        long now = System.currentTimeMillis();
        if (CACHED_VANILLA_BLOCK_KEYS != null && now - VANILLA_CACHE_STAMP < 60_000) {
            return CACHED_VANILLA_BLOCK_KEYS;
        }
        List<String> keys = Arrays.stream(Material.values())
                .filter(Material::isBlock)
                .map(m -> "minecraft:" + m.getKey().getKey()) // namespaced lower-case
                .sorted()
                .collect(Collectors.toList());
        CACHED_VANILLA_BLOCK_KEYS = keys;
        VANILLA_CACHE_STAMP = now;
        return keys;
    }

    /** Cached list of vanilla enum names like STONE for every block Material. */
    private static List<String> getAllVanillaBlockEnum() {
        long now = System.currentTimeMillis();
        if (CACHED_VANILLA_BLOCK_ENUM != null && now - VANILLA_CACHE_STAMP < 60_000) {
            return CACHED_VANILLA_BLOCK_ENUM;
        }
        List<String> enums = Arrays.stream(Material.values())
                .filter(Material::isBlock)
                .map(Enum::name)
                .sorted()
                .collect(Collectors.toList());
        CACHED_VANILLA_BLOCK_ENUM = enums;
        VANILLA_CACHE_STAMP = now;
        return enums;
    }

    /**
     * Cached list of `itemadder:<namespaced_id>` if ItemsAdder is present.
     * Uses reflection, so there is no hard compile dependency.
     */
    @SuppressWarnings("unchecked")
    private static List<String> getItemsAdderBlockKeys() {
        long now = System.currentTimeMillis();
        if (CACHED_IA_BLOCK_KEYS != null && now - IA_CACHE_STAMP < 60_000) {
            return CACHED_IA_BLOCK_KEYS;
        }
        try {
            // dev.lone.itemsadder.api.CustomBlock#getCustomBlocks(): Collection<CustomBlock>
            Class<?> cbClass = Class.forName("dev.lone.itemsadder.api.CustomBlock");
            Method getAll = null;
            try {
                getAll = cbClass.getMethod("getCustomBlocks");
            } catch (NoSuchMethodException ignored) {}

            Collection<?> blocks;
            if (getAll != null) {
                blocks = (Collection<?>) getAll.invoke(null);
            } else {
                // Fallback: try dev.lone.itemsadder.api.ItemsAdder.getCustomBlocks()
                Class<?> iaMain = Class.forName("dev.lone.itemsadder.api.ItemsAdder");
                Method alt = iaMain.getMethod("getCustomBlocks");
                blocks = (Collection<?>) alt.invoke(null);
            }

            Method getId = cbClass.getMethod("getNamespacedID"); // String like itemsadder:blue_bricks
            List<String> out = new ArrayList<>();
            for (Object o : blocks) {
                Object id = getId.invoke(o);
                if (id != null) {
                    String ns = id.toString();
                    // Normalize to itemadder: prefix (ItemsAdder usually already uses that)
                    if (!ns.toLowerCase(Locale.ROOT).startsWith("itemadder:")) {
                        int i = ns.indexOf(':');
                        ns = "itemadder:" + (i >= 0 ? ns.substring(i + 1) : ns);
                    }
                    out.add(ns);
                }
            }
            out.sort(String::compareToIgnoreCase);
            CACHED_IA_BLOCK_KEYS = out;
            IA_CACHE_STAMP = now;
            return out;
        } catch (Throwable ignored) {
            CACHED_IA_BLOCK_KEYS = Collections.emptyList();
            IA_CACHE_STAMP = now;
            return CACHED_IA_BLOCK_KEYS;
        }
    }

    /* ---------- small safe parsers ---------- */
    private static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception ignored) { return def; }
    }
    private static float parseFloat(String s, float def) {
        try { return Float.parseFloat(s); } catch (Exception ignored) { return def; }
    }
    private static int safeParseInt(String[] arr, int idx, int def) {
        if (idx < 0 || idx >= arr.length) return def;
        try { return Integer.parseInt(arr[idx]); } catch (Exception ignored) { return def; }
    }
    private static long safeParseLong(String s, long def) {
        try { return Long.parseLong(s); } catch (Exception ignored) { return def; }
    }
}
