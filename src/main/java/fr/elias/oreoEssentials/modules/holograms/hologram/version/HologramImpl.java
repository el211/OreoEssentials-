package fr.elias.oreoEssentials.modules.holograms.hologram.version;

import fr.elias.oreoEssentials.modules.holograms.OHolograms;
import fr.elias.oreoEssentials.modules.holograms.api.data.BlockHologramData;
import fr.elias.oreoEssentials.modules.holograms.api.data.DisplayHologramData;
import fr.elias.oreoEssentials.modules.holograms.api.data.HologramData;
import fr.elias.oreoEssentials.modules.holograms.api.data.ItemHologramData;
import fr.elias.oreoEssentials.modules.holograms.api.data.TextHologramData;
import fr.elias.oreoEssentials.modules.holograms.api.events.HologramHideEvent;
import fr.elias.oreoEssentials.modules.holograms.api.events.HologramShowEvent;
import fr.elias.oreoEssentials.modules.holograms.api.hologram.Hologram;
import fr.elias.oreoEssentials.modules.holograms.nms.NmsBridgeLoader;
import fr.elias.oreoEssentials.modules.holograms.nms.NmsHologramBridge;
import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.Collection;
import java.util.UUID;

public final class HologramImpl extends Hologram {

    private static final @Nullable NmsHologramBridge TEXT_BRIDGE = loadTextBridge();
    private @Nullable UUID entityUuid;
    private int entityId = -1;

    public HologramImpl(@NotNull final HologramData data) {
        super(data);
    }

    @Override
    public int getEntityId() {
        return entityId;
    }

    @Override
    public @Nullable Display getDisplayEntity() {
        Entity entity = resolveEntity();
        return entity instanceof Display display ? display : null;
    }

    @Override
    protected void create() {
        final Location location = normalizedDisplayLocation();
        if (location.getWorld() == null || !location.isWorldLoaded()) {
            return;
        }

        Display display = getDisplayEntity();
        if (display == null) {
            removeStaleDisplays(location);
            display = switch (data.getType()) {
                case TEXT -> (Display) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
                case ITEM -> (Display) location.getWorld().spawnEntity(location, EntityType.ITEM_DISPLAY);
                case BLOCK -> (Display) location.getWorld().spawnEntity(location, EntityType.BLOCK_DISPLAY);
            };
            entityUuid = display.getUniqueId();
            entityId = display.getEntityId();
        }

        display.setPersistent(false);
        display.addScoreboardTag("oe_hologram");
        display.addScoreboardTag("oe_hologram:" + data.getName().toLowerCase());

        update();
    }

    @Override
    protected void delete() {
        Entity entity = resolveEntity();
        if (entity != null) {
            entity.remove();
        }
        entityUuid = null;
        entityId = -1;
    }

    @Override
    protected void update() {
        final Display display = getDisplayEntity();
        if (display == null) {
            return;
        }

        final Location location = normalizedDisplayLocation();
        if (location.getWorld() == null || !location.isWorldLoaded()) {
            return;
        }

        if (OreScheduler.isFolia()) {
            display.teleportAsync(location);
        } else {
            display.teleport(location);
        }

        if (data instanceof TextHologramData textData && display instanceof TextDisplay textDisplay) {
            textDisplay.text(getShownText(null));

            final Color background = textData.getBackground();
            if (background == null) {
                textDisplay.setDefaultBackground(true);
            } else if (background == Hologram.TRANSPARENT) {
                textDisplay.setDefaultBackground(false);
                textDisplay.setBackgroundColor(Color.fromARGB(0));
            } else {
                textDisplay.setDefaultBackground(false);
                textDisplay.setBackgroundColor(background);
            }

            textDisplay.setShadowed(textData.hasTextShadow());
            textDisplay.setSeeThrough(textData.isSeeThrough());
            textDisplay.setLineWidth(Hologram.LINE_WIDTH);
            textDisplay.setAlignment(textData.getTextAlignment());
        } else if (data instanceof ItemHologramData itemData && display instanceof ItemDisplay itemDisplay) {
            itemDisplay.setItemStack(itemData.getItemStack());
        } else if (data instanceof BlockHologramData blockData && display instanceof BlockDisplay blockDisplay) {
            blockDisplay.setBlock(blockData.getBlock().createBlockData());
        }

        if (data instanceof DisplayHologramData displayData) {
            display.setBillboard(displayData.getBillboard());
            display.setBrightness(displayData.getBrightness());
            display.setShadowRadius(displayData.getShadowRadius());
            display.setShadowStrength(displayData.getShadowStrength());
            display.setInterpolationDuration(displayData.getInterpolationDuration());
            display.setTeleportDuration(0);
            display.setViewRange(displayData.getVisibilityDistance());
            display.setTransformation(new Transformation(
                    displayData.getTranslation(),
                    new Quaternionf(),
                    displayData.getScale(),
                    new Quaternionf()
            ));
        }
    }

    @Override
    protected boolean show(@NotNull final Player player) {
        if (!new HologramShowEvent(this, player).callEvent()) {
            return false;
        }

        Display display = getDisplayEntity();
        if (display == null) {
            create();
            display = getDisplayEntity();
        } else {
            update();
        }

        final Entity entity = display;
        if (entity == null) {
            return false;
        }

        player.showEntity(OHolograms.get().getPlugin(), entity);
        viewers.add(player.getUniqueId());
        refresh(player);
        return true;
    }

    @Override
    protected boolean hide(@NotNull final Player player) {
        if (!new HologramHideEvent(this, player).callEvent()) {
            return false;
        }

        final Entity entity = resolveEntity();
        if (entity == null) {
            return false;
        }

        player.hideEntity(OHolograms.get().getPlugin(), entity);
        viewers.remove(player.getUniqueId());
        return true;
    }

    @Override
    protected void refresh(@NotNull final Player player) {
        if (!isViewer(player)) {
            return;
        }

        final Display display = getDisplayEntity();
        if (display == null) {
            return;
        }

        if (display instanceof TextDisplay textDisplay) {
            final var baseText = getShownText(null);
            final var playerText = getShownText(player);
            textDisplay.text(baseText);
            if (TEXT_BRIDGE != null && playerText != null && !playerText.equals(baseText)) {
                TEXT_BRIDGE.sendTextDisplayText(player, entityId, playerText);
            }
        }

        if (!player.canSee(display)) {
            player.showEntity(OHolograms.get().getPlugin(), display);
        }
    }

    private @Nullable Entity resolveEntity() {
        if (entityUuid == null) {
            return null;
        }
        return org.bukkit.Bukkit.getEntity(entityUuid);
    }

    private @NotNull Location normalizedDisplayLocation() {
        Location location = data.getLocation().clone();
        location.setYaw(0.0f);
        location.setPitch(0.0f);
        return location;
    }

    private void removeStaleDisplays(@NotNull Location location) {
        if (location.getWorld() == null) {
            return;
        }

        Collection<Entity> nearby = location.getWorld().getNearbyEntities(location, 0.2, 0.2, 0.2);
        for (Entity entity : nearby) {
            if (!(entity instanceof Display display)) {
                continue;
            }
            if (entity.getType() != expectedEntityType()) {
                continue;
            }
            entity.remove();
        }
    }

    private @NotNull EntityType expectedEntityType() {
        return switch (data.getType()) {
            case TEXT -> EntityType.TEXT_DISPLAY;
            case ITEM -> EntityType.ITEM_DISPLAY;
            case BLOCK -> EntityType.BLOCK_DISPLAY;
        };
    }

    private static @Nullable NmsHologramBridge loadTextBridge() {
        try {
            return NmsBridgeLoader.loadOrThrow();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
