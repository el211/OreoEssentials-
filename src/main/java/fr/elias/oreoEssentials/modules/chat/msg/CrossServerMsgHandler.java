package fr.elias.oreoEssentials.modules.chat.msg;

import fr.elias.oreoEssentials.services.MessageService;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

public class CrossServerMsgHandler implements PacketSubscriber<CrossServerMsgPacket> {

    private final MessageService messageService;

    public CrossServerMsgHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void onReceive(PacketChannel channel, CrossServerMsgPacket packet) {
        Bukkit.getScheduler().runTask(
                Bukkit.getPluginManager().getPlugin("OreoEssentials"),
                () -> {
                    Player target = Bukkit.getPlayer(packet.getTargetUuid());
                    if (target == null || !target.isOnline()) return;

                    Lang.send(target, "msg.receive",
                            "<gray>[<light_purple>MSG</light_purple>] <aqua>%sender%</aqua>: <white>%message%</white></gray>",
                            Map.of("sender", packet.getSenderName(), "message", packet.getMessage()));

                    // Record so the target can /reply back cross-server
                    messageService.record(packet.getSenderUuid(), packet.getTargetUuid());
                }
        );
    }
}
