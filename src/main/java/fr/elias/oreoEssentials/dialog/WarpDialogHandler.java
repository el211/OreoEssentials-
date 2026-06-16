package fr.elias.oreoEssentials.dialog;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.warps.WarpService;
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
import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
public final class WarpDialogHandler {

    private WarpDialogHandler() {}

    public static void open(OreoEssentials plugin, Player player, WarpService warps) {
        Async.run(() -> {
            Set<String> all = warps.listWarps();
            OreScheduler.runForEntity(plugin, player, () -> {
                if (!player.isOnline()) return;

                List<String> accessible = all == null ? List.of() :
                        all.stream()
                                .sorted(String.CASE_INSENSITIVE_ORDER)
                                .filter(name -> warps.canUse(player, name))
                                .toList();

                if (accessible.isEmpty()) {
                    Lang.send(player, "warp.no-warps", "<yellow>No warps available.</yellow>");
                    return;
                }

                List<ActionButton> buttons = new ArrayList<>();
                for (String name : accessible) {
                    String cmd = "warp " + name;
                    buttons.add(ActionButton.builder(Component.text("\u2691 " + name, NamedTextColor.GOLD))
                            .tooltip(Component.text("Teleport to warp: " + name, NamedTextColor.GRAY))
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
                        .base(DialogBase.builder(Component.text("Server Warps", NamedTextColor.DARK_AQUA))
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
