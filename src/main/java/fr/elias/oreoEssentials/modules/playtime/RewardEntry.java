package fr.elias.oreoEssentials.modules.playtime;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

public final class RewardEntry {
    public final String id;
    public final String displayName;
    public final boolean autoClaim;
    public final List<String> description;

    public final Long payFor;
    public final Long payEvery;

    public final boolean stackRewards;
    public final boolean requiresPermission;  // require oreo.prewards.<id>

    public final List<String> commands;

    public final Integer slot;
    public final Material iconMaterial;
    public final Integer customModelData;
    public final String iconName;
    public final List<String> iconLore;

    public RewardEntry(
            String id,
            String displayName,
            boolean autoClaim,
            List<String> description,
            Long payFor,
            Long payEvery,
            boolean stackRewards,
            boolean requiresPermission,
            List<String> commands,
            Integer slot,
            Material iconMaterial,
            Integer customModelData,
            String iconName,
            List<String> iconLore
    ) {
        this.id = id;
        this.displayName = displayName;
        this.autoClaim = autoClaim;
        this.description = (description != null) ? description : Collections.emptyList();

        this.payFor = payFor;
        this.payEvery = payEvery;

        this.stackRewards = stackRewards;
        this.requiresPermission = requiresPermission;

        this.commands = (commands != null) ? commands : Collections.emptyList();

        this.slot = slot;
        this.iconMaterial = (iconMaterial != null) ? iconMaterial : Material.PAPER;
        this.customModelData = customModelData;
        this.iconName = iconName;
        this.iconLore = (iconLore != null) ? iconLore : Collections.emptyList();
    }

    public boolean isOneTime() {
        return payFor != null && payFor > 0;
    }

    public boolean isRepeating() {
        return payEvery != null && payEvery > 0;
    }
}
