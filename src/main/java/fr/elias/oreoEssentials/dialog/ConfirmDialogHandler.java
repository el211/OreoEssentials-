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
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Generic reusable confirmation dialog (Confirm / Cancel).
 * confirmCmd is run via performCommand on confirm; cancel closes the dialog.
 *
 * SECURITY: callers must pass pre-sanitized command strings. Dynamic user-controlled
 * parts (home names, warp names, etc.) must be sanitized via {@link #sanitizeCommandArg}
 * before being embedded in the command string.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ConfirmDialogHandler {

    private ConfirmDialogHandler() {}

    /**
     * Strips any character that is not alphanumeric, underscore, hyphen, or period
     * from a user-supplied command argument to prevent command injection.
     */
    public static String sanitizeCommandArg(String s) {
        if (s == null) return "";
        return s.replaceAll("[^a-zA-Z0-9_\\-.]", "");
    }

    public static void open(OreoEssentials plugin, Player player,
                            Component title, Component body,
                            String confirmCmd) {
        OreScheduler.runForEntity(plugin, player, () -> {
            if (!player.isOnline()) return;

            ActionButton confirmBtn = ActionButton.builder(Component.text("\u2714 Confirm", NamedTextColor.GREEN))
                    .tooltip(Component.text("Proceed with this action", NamedTextColor.GRAY))
                    .action(DialogAction.customClick(
                            (view, audience) -> {
                                if (audience instanceof Player p) p.performCommand(confirmCmd);
                            },
                            ClickCallback.Options.builder()
                                    .uses(1)
                                    .lifetime(ClickCallback.DEFAULT_LIFETIME)
                                    .build()
                    ))
                    .build();

            ActionButton cancelBtn = ActionButton.builder(Component.text("\u2716 Cancel", NamedTextColor.RED))
                    .tooltip(Component.text("Cancel this action", NamedTextColor.GRAY))
                    .action(null)
                    .build();

            Dialog dialog = Dialog.create(b -> b.empty()
                    .base(DialogBase.builder(title)
                            .canCloseWithEscape(true)
                            .body(List.of(DialogBody.plainMessage(body)))
                            .build())
                    .type(DialogType.confirmation(confirmBtn, cancelBtn))
            );

            player.showDialog(dialog);
        });
    }
}
