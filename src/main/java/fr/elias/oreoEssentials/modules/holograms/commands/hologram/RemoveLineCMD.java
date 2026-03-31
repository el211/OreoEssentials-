package fr.elias.oreoEssentials.modules.holograms.commands.hologram;

import com.google.common.primitives.Ints;
import fr.elias.oreoEssentials.modules.holograms.api.hologram.Hologram;
import fr.elias.oreoEssentials.modules.holograms.api.data.TextHologramData;
import fr.elias.oreoEssentials.modules.holograms.commands.Subcommand;
import de.oliver.fancylib.MessageHelper;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RemoveLineCMD implements Subcommand {

    @Override
    public List<String> tabcompletion(@NotNull CommandSender player, @Nullable Hologram hologram, @NotNull String[] args) {
        return null;
    }

    @Override
    public boolean run(@NotNull CommandSender player, @Nullable Hologram hologram, @NotNull String[] args) {

        if (!(player.hasPermission("OHolograms.hologram.edit.line.remove"))) {
            MessageHelper.error(player, "You don't have the required permission to remove a line from a hologram");
            return false;
        }

        if (!(hologram.getData() instanceof TextHologramData)) {
            MessageHelper.error(player, "This command can only be used on text holograms");
            return false;
        }

        final var index = Ints.tryParse(args[3]);
        if (index == null) {
            MessageHelper.error(player, "Could not parse line number");
            return false;
        }

        return SetLineCMD.setLine(player, hologram, index - 1, null);
    }
}
