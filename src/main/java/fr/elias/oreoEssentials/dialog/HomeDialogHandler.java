package fr.elias.oreoEssentials.dialog;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.homes.home.HomeService;
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
public final class HomeDialogHandler {

    private HomeDialogHandler() {}

    public static void open(OreoEssentials plugin, Player player, HomeService homes) {
        Async.run(() -> {
            Set<String> names = homes.allHomeNames(player.getUniqueId());
            OreScheduler.runForEntity(plugin, player, () -> {
                if (!player.isOnline()) return;

                if (names == null || names.isEmpty()) {
                    Lang.send(player, "home.no-homes",
                            "<yellow>You have no homes. Use <aqua>/sethome</aqua> to create one.</yellow>");
                    return;
                }

                List<String> sorted = names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
                List<ActionButton> buttons = new ArrayList<>();

                for (String name : sorted) {
                    String cmd = "home " + name;
                    buttons.add(ActionButton.builder(Component.text("\uD83C\uDFE0 " + name, NamedTextColor.AQUA))
                            .tooltip(Component.text("Teleport to home: " + name, NamedTextColor.GRAY))
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
                        .base(DialogBase.builder(Component.text("Your Homes", NamedTextColor.DARK_GREEN))
                                .canCloseWithEscape(true)
                                .body(List.of(
                                        DialogBody.plainMessage(
                                                Component.text("Click a home to teleport.", NamedTextColor.GRAY)
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
