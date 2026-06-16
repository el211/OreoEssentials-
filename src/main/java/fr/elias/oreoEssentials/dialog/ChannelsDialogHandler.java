package fr.elias.oreoEssentials.dialog;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannel;
import fr.elias.oreoEssentials.modules.chat.channels.ChatChannelManager;
import fr.elias.oreoEssentials.util.OreScheduler;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class ChannelsDialogHandler {

    private ChannelsDialogHandler() {}

    public static void open(OreoEssentials plugin, Player player, ChatChannelManager channelManager) {
        OreScheduler.runForEntity(plugin, player, () -> {
            if (!player.isOnline()) return;

            List<ChatChannel> accessible = channelManager.getAllChannels().stream()
                    .filter(ChatChannel::isEnabled)
                    .filter(c -> c.canJoin(player))
                    .toList();

            if (accessible.isEmpty()) {
                player.sendMessage(Component.text("No channels available.", NamedTextColor.YELLOW));
                return;
            }

            ChatChannel current = channelManager.getPlayerChannel(player);

            List<ActionButton> buttons = new ArrayList<>();
            for (ChatChannel ch : accessible) {
                String id = ch.getId();
                String displayName = ch.getDisplayName();
                boolean isCurrent = current != null && current.getId().equals(id);
                String cmd = "oechannel " + id;

                String descLine = (ch.getDescription() != null && !ch.getDescription().isBlank())
                        ? ch.getDescription() : "Switch to this channel";
                String tooltipText = (isCurrent ? "[Current] " : "") + descLine;

                NamedTextColor color = isCurrent ? NamedTextColor.YELLOW : NamedTextColor.WHITE;
                String label = (isCurrent ? "\u2605 " : "\u2606 ") + displayName;

                buttons.add(ActionButton.builder(Component.text(label, color))
                        .tooltip(Component.text(tooltipText, NamedTextColor.GRAY))
                        .action(DialogAction.customClick(
                                (view, audience) -> {
                                    if (audience instanceof Player p) p.performCommand(cmd);
                                },
                                ClickCallback.Options.builder()
                                        .uses(1)
                                        .lifetime(ClickCallback.DEFAULT_LIFETIME)
                                        .build()
                        ))
                        .build());
            }

            buttons.add(ActionButton.create(
                    Component.text("\u2716 Close", NamedTextColor.RED),
                    Component.text("Close this menu", NamedTextColor.GRAY),
                    100,
                    null
            ));

            Dialog dialog = Dialog.create(b -> b.empty()
                    .base(DialogBase.builder(Component.text("Chat Channels", NamedTextColor.LIGHT_PURPLE))
                            .canCloseWithEscape(true)
                            .body(List.of(DialogBody.plainMessage(
                                    Component.text("Click a channel to switch to it.", NamedTextColor.GRAY))))
                            .build())
                    .type(DialogType.multiAction(buttons).build())
            );

            player.showDialog(dialog);
        });
    }
}
