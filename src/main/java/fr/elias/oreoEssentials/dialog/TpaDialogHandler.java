package fr.elias.oreoEssentials.dialog;

import fr.elias.oreoEssentials.OreoEssentials;
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

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class TpaDialogHandler {

    private TpaDialogHandler() {}

    /**
     * Sends a TPA request dialog to the target player.
     * Shows an Accept / Deny dialog on top of the regular chat notification.
     * GeyserMC will automatically convert this to a Bedrock form for Bedrock players.
     *
     * @param target    the player being asked
     * @param requester the player who sent /tpa or /tpahere
     * @param isHere    true if /tpahere (requester wants target to come to them)
     */
    public static void sendRequest(OreoEssentials plugin, Player target, Player requester, boolean isHere) {
        OreScheduler.runForEntity(plugin, target, () -> {
            if (!target.isOnline()) return;

            String requesterName = requester.getName();
            String bodyText = isHere
                    ? requesterName + " wants you to teleport to them."
                    : requesterName + " wants to teleport to you.";

            ActionButton acceptBtn = ActionButton.builder(
                            Component.text("\u2714 Accept", NamedTextColor.GREEN))
                    .tooltip(Component.text("Accept this teleport request", NamedTextColor.GRAY))
                    .action(DialogAction.customClick(
                            (view, audience) -> {
                                if (audience instanceof Player p) p.performCommand("tpaccept");
                            },
                            ClickCallback.Options.builder()
                                    .uses(1)
                                    .lifetime(ClickCallback.DEFAULT_LIFETIME)
                                    .build()
                    ))
                    .build();

            ActionButton denyBtn = ActionButton.builder(
                            Component.text("\u2718 Deny", NamedTextColor.RED))
                    .tooltip(Component.text("Deny this teleport request", NamedTextColor.GRAY))
                    .action(DialogAction.customClick(
                            (view, audience) -> {
                                if (audience instanceof Player p) p.performCommand("tpdeny");
                            },
                            ClickCallback.Options.builder()
                                    .uses(1)
                                    .lifetime(ClickCallback.DEFAULT_LIFETIME)
                                    .build()
                    ))
                    .build();

            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(DialogBase.builder(Component.text("Teleport Request", NamedTextColor.GOLD))
                            .canCloseWithEscape(false)
                            .body(List.of(
                                    DialogBody.plainMessage(Component.text(bodyText, NamedTextColor.GRAY))
                            ))
                            .build()
                    )
                    .type(DialogType.confirmation(acceptBtn, denyBtn))
            );

            target.showDialog(dialog);
        });
    }
}
