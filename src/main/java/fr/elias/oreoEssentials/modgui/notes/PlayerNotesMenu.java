// package: fr.elias.oreoEssentials.modgui.notes
package fr.elias.oreoEssentials.modgui.notes;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class PlayerNotesMenu implements InventoryProvider {

    private final OreoEssentials plugin;
    private final PlayerNotesManager manager;
    private final NotesChatListener chat;
    private final UUID target;

    public PlayerNotesMenu(OreoEssentials plugin,
                           PlayerNotesManager manager,
                           NotesChatListener chat,
                           UUID target) {
        this.plugin = plugin;
        this.manager = manager;
        this.chat = chat;
        this.target = target;
    }

    @Override
    public void init(Player p, InventoryContents c) {
        List<String> notes = manager.getNotes(target);

        int row = 1, col = 1;
        for (int i = notes.size() - 1; i >= 0; i--) {
            String line = notes.get(i);
            if (row >= 5) break;
            c.set(row, col, ClickableItem.empty(
                    new ItemBuilder(Material.PAPER)
                            .name("&fNote")
                            .lore("&7" + line)
                            .build()
            ));
            if (++col >= 8) { col = 1; row++; }
        }

        // Add note button
        c.set(5, 4, ClickableItem.of(
                new ItemBuilder(Material.FEATHER)
                        .name("&aAdd note")
                        .lore("&7Click, then type note in chat.")
                        .build(),
                e -> {
                    chat.startNote(p, target);
                    p.closeInventory();
                }
        ));
    }

    @Override public void update(Player player, InventoryContents contents) {}
}
