package fr.elias.oreoEssentials.dialog;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.kits.Kit;
import fr.elias.oreoEssentials.modules.kits.KitsManager;
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
import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public final class KitsDialogHandler {

    private KitsDialogHandler() {}

    public static void open(OreoEssentials plugin, KitsManager manager, Player player) {
        OreScheduler.runForEntity(plugin, player, () -> {
            if (!player.isOnline()) return;

            Map<String, Kit> kits = manager.getKits();
            if (kits == null || kits.isEmpty()) {
                player.sendMessage(Component.text("No kits are available.", NamedTextColor.YELLOW));
                return;
            }

            List<ActionButton> buttons = new ArrayList<>();
            for (Map.Entry<String, Kit> entry : kits.entrySet()) {
                String id = entry.getKey();
                Kit kit = entry.getValue();
                String label = (kit.getDisplayName() != null && !kit.getDisplayName().isBlank())
                        ? kit.getDisplayName() : id;
                String cmd = "kit " + id;
                String cooldownText = kit.getCooldownSeconds() > 0
                        ? formatCooldown(kit.getCooldownSeconds()) : "No cooldown";

                buttons.add(ActionButton.builder(Component.text("\uD83C\uDF81 " + label, NamedTextColor.GREEN))
                        .tooltip(Component.text("ID: " + id + " | Cooldown: " + cooldownText, NamedTextColor.GRAY))
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
                    .base(DialogBase.builder(Component.text("Kits", NamedTextColor.GREEN))
                            .canCloseWithEscape(true)
                            .body(List.of(DialogBody.plainMessage(
                                    Component.text("Click a kit to claim it.", NamedTextColor.GRAY))))
                            .build())
                    .type(DialogType.multiAction(buttons).build())
            );

            player.showDialog(dialog);
        });
    }

    private static String formatCooldown(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        if (seconds < 86400) return (seconds / 3600) + "h";
        return (seconds / 86400) + "d";
    }
}
