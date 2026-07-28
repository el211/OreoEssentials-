package fr.elias.oreoEssentials.dialog;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.playerwarp.PlayerWarp;
import fr.elias.oreoEssentials.modules.playerwarp.PlayerWarpService;
import fr.elias.oreoEssentials.util.Async;
import fr.elias.oreoEssentials.util.Lang;
import fr.elias.oreoEssentials.util.OreScheduler;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class PlayerWarpDialogHandler {

    private PlayerWarpDialogHandler() {}

    public static void open(OreoEssentials plugin, Player player) {
        PlayerWarpService service;
        try {
            service = plugin.getPlayerWarpService();
        } catch (Throwable t) {
            return;
        }
        if (service == null) return;

        Async.run(() -> {
            List<PlayerWarp> all;
            try {
                all = new ArrayList<>(service.listAll());
            } catch (Throwable t) {
                all = List.of();
            }
            final List<PlayerWarp> warps = all;

            OreScheduler.runForEntity(plugin, player, () -> {
                if (!player.isOnline()) return;

                List<PlayerWarp> accessible = warps.stream()
                        .filter(w -> !w.isLocked() || service.canUse(player, w))
                        .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                        .toList();

                if (accessible.isEmpty()) {
                    Lang.send(player, "playerwarp.no-warps",
                            "<yellow>No player warps available.</yellow>");
                    return;
                }

                List<ActionButton> buttons = new ArrayList<>();
                for (PlayerWarp w : accessible) {
                    String cmd = "pw " + w.getName();
                    String label = w.getDescription() != null && !w.getDescription().isBlank()
                            ? w.getName() + " \u2014 " + w.getDescription()
                            : w.getName();
                    String tooltip = "Teleport to " + w.getName();
                    buttons.add(ActionButton.builder(
                                    Component.text("\u2726 " + label, NamedTextColor.LIGHT_PURPLE))
                            .tooltip(Component.text(tooltip, NamedTextColor.GRAY))
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

                // null action = close the dialog without doing anything
                buttons.add(ActionButton.create(
                        Component.text("\u2716 Close", NamedTextColor.RED),
                        Component.text("Close this menu", NamedTextColor.GRAY),
                        100,
                        null
                ));

                Dialog dialog = Dialog.create(builder -> builder.empty()
                        .base(DialogBase.builder(Component.text("Player Warps", NamedTextColor.LIGHT_PURPLE))
                                .canCloseWithEscape(true)
                                .body(List.of(
                                        DialogBody.plainMessage(
                                                Component.text("Click a warp to teleport.", NamedTextColor.GRAY)
                                        )
                                ))
                                .build()
                        )
                        .type(DialogType.multiAction(buttons).build())
                );

                player.showDialog(dialog);
            });
        });
    }
}
