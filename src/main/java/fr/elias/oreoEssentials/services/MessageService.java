package fr.elias.oreoEssentials.services;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MessageService {
    private final Map<UUID, UUID> lastReply = new ConcurrentHashMap<>();

    public void record(Player sender, Player target) {
        lastReply.put(sender.getUniqueId(), target.getUniqueId());
        lastReply.put(target.getUniqueId(), sender.getUniqueId());
    }

    public UUID getLast(UUID who) { return lastReply.get(who); }
}

