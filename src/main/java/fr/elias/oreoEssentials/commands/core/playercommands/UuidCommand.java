package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UuidCommand implements OreoCommand {
    @Override public String name() { return "uuid"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.uuid"; }
    @Override public String usage() { return "[player]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        OfflinePlayer target;
        if (args.length >= 1) {
            if (!sender.hasPermission("oreo.uuid.others")) {
                Lang.send(sender, "uuid.no-permission-others",
                        "<red>You don't have permission to view others' UUIDs.</red>");
                return true;
            }
            target = Bukkit.getOfflinePlayer(args[0]);
        } else {
            if (!(sender instanceof Player p)) {
                Lang.send(sender, "uuid.usage",
                        "<yellow>Usage: /%label% <player></yellow>",
                        Map.of("label", label));
                return true;
            }
            target = p;
        }

        String display = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        UUID uuid = target.getUniqueId();

        Lang.send(sender, "uuid.show",
                "<gold>UUID of <aqua>%player%</aqua>:</gold> <aqua>%uuid%</aqua>",
                Map.of("player", display, "uuid", uuid.toString()));

        try {
            Class<?> apiCls = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiCls.getMethod("getInstance").invoke(null);

            boolean isBedrock = (boolean) apiCls.getMethod("isFloodgatePlayer", UUID.class).invoke(api, uuid);
            if (isBedrock) {
                Object fgPlayer = apiCls.getMethod("getPlayer", UUID.class).invoke(api, uuid);
                UUID bedrockUuid = null;

                try {
                    bedrockUuid = (UUID) fgPlayer.getClass().getMethod("getCorrectUniqueId").invoke(fgPlayer);
                } catch (NoSuchMethodException ignored1) {
                    try {
                        bedrockUuid = (UUID) fgPlayer.getClass().getMethod("getJavaUniqueId").invoke(fgPlayer);
                    } catch (NoSuchMethodException ignored2) {
                        try {
                            bedrockUuid = (UUID) fgPlayer.getClass().getMethod("getUniqueId").invoke(fgPlayer);
                        } catch (NoSuchMethodException ignored3) {
                            bedrockUuid = uuid;
                        }
                    }
                }

                Lang.send(sender, "uuid.bedrock",
                        "<gray>Bedrock UUID: <aqua>%bedrock_uuid%</aqua></gray>",
                        Map.of("bedrock_uuid", bedrockUuid.toString()));
            }
        } catch (ClassNotFoundException e) {
        } catch (Throwable t) {
            OreoEssentials.get().getLogger().fine("[/uuid] Floodgate reflect failed: " + t.getMessage());
        }

        return true;
    }
}