package fr.elias.oreoEssentials.util;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ProxyMessenger implements PluginMessageListener {

    private static final String BUNGEE_CHANNEL = "BungeeCord"; // works on Bungee & Velocity (compat)
    private final Plugin plugin;
    private final List<String> cachedServers = new CopyOnWriteArrayList<>();

    public ProxyMessenger(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
    }

    public void requestServers() {
        Player any = getAnyPlayer();
        if (any == null) return; // need a player context to send plugin message
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("GetServers");
            any.sendPluginMessage(plugin, BUNGEE_CHANNEL, b.toByteArray());
        } catch (Exception ignored) {}
    }

    public void connect(Player player, String serverName) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, b.toByteArray());
        } catch (Exception ignored) {}
    }

    public List<String> getCachedServers() {
        return new ArrayList<>(cachedServers);
    }

    public void sendToServer(Player player, String serverName) {
        if (player == null || serverName == null || serverName.isBlank()) return;

        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(serverName);

            player.sendPluginMessage(
                    plugin,
                    "BungeeCord",
                    out.toByteArray()
            );
        } catch (Throwable t) {
            plugin.getLogger().warning("[ProxyMessenger] Failed to send "
                    + player.getName() + " to server " + serverName + ": " + t.getMessage());
        }
    }
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!BUNGEE_CHANNEL.equals(channel)) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String sub = in.readUTF();
            if ("GetServers".equalsIgnoreCase(sub)) {
                String csv = in.readUTF();
                List<String> list = new ArrayList<>();
                for (String s : csv.split(", ")) {
                    if (!s.isEmpty()) list.add(s);
                }
                cachedServers.clear();
                cachedServers.addAll(list);
            }
        } catch (Exception ignored) {}
    }

    private Player getAnyPlayer() {
        for (Player p : Bukkit.getOnlinePlayers()) return p;
        return null;
    }
}
