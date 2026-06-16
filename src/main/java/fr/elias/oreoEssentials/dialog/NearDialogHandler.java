package fr.elias.oreoEssentials.dialog;

import fr.elias.oreoEssentials.OreoEssentials;
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
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class NearDialogHandler {

    private NearDialogHandler() {}

    public static void open(OreoEssentials plugin, Player player, int radius) {
        OreScheduler.runForEntity(plugin, player, () -> {
            if (!player.isOnline()) return;

            Location me = player.getLocation();
            List<Player> nearby = new ArrayList<>();
            for (Player other : player.getWorld().getPlayers()) {
                if (other == player) continue;
                if (other.getLocation().distance(me) <= radius) nearby.add(other);
            }
            nearby.sort(Comparator.comparingDouble(o -> o.getLocation().distance(me)));

            if (nearby.isEmpty()) {
                Dialog dialog = Dialog.create(b -> b.empty()
                        .base(DialogBase.builder(Component.text("Nearby Players", NamedTextColor.GOLD))
                                .canCloseWithEscape(true)
                                .body(List.of(DialogBody.plainMessage(
                                        Component.text("No players within " + radius + " blocks.", NamedTextColor.GRAY))))
                                .build())
                        .type(DialogType.notice(ActionButton.create(
                                Component.text("\u2716 Close", NamedTextColor.RED),
                                Component.text("Close this dialog", NamedTextColor.GRAY),
                                100,
                                null
                        )))
                );
                player.showDialog(dialog);
                return;
            }

            List<ActionButton> buttons = new ArrayList<>();
            for (Player other : nearby) {
                double dist = Math.round(other.getLocation().distance(me) * 10.0) / 10.0;
                String name = other.getName();
                String cmd = "tp " + name;

                buttons.add(ActionButton.builder(Component.text("\uD83D\uDC64 " + name, NamedTextColor.AQUA))
                        .tooltip(Component.text(dist + "m \u2022 " + other.getWorld().getName(), NamedTextColor.GRAY))
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
                    .base(DialogBase.builder(Component.text("Nearby Players (" + radius + "m)", NamedTextColor.GOLD))
                            .canCloseWithEscape(true)
                            .body(List.of(DialogBody.plainMessage(
                                    Component.text("Click a player to teleport to them.", NamedTextColor.GRAY))))
                            .build())
                    .type(DialogType.multiAction(buttons).build())
            );

            player.showDialog(dialog);
        });
    }
}
