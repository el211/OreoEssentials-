package fr.elias.oreoEssentials.modules.invsee;

import fr.elias.oreoEssentials.modules.invsee.menu.InvseeMenu;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;


public final class InvseeSession {

    private final UUID targetId;
    private final UUID viewerId;
    private String targetName;

    private ItemStack[] lastSnapshot;
    private InvseeMenu menu;

    public InvseeSession(UUID targetId, UUID viewerId) {
        this.targetId = targetId;
        this.viewerId = viewerId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public UUID getViewerId() {
        return viewerId;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getTargetNameOrFallback() {
        return (targetName != null && !targetName.isEmpty())
                ? targetName
                : "Player";
    }

    public ItemStack[] getLastSnapshot() {
        return lastSnapshot;
    }

    public void setLastSnapshot(ItemStack[] lastSnapshot) {
        this.lastSnapshot = lastSnapshot;
    }

    public InvseeMenu getMenu() {
        return menu;
    }

    public void setMenu(InvseeMenu menu) {
        this.menu = menu;
    }
}
