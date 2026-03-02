package fr.elias.oreoEssentials.modules.shop.managers;

import fr.elias.oreoEssentials.modules.shop.ShopModule;
import fr.elias.oreoEssentials.modules.shop.models.PriceModifier;
import fr.elias.oreoEssentials.modules.shop.models.ShopItem;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PriceModifierManager {

    private final ShopModule module;

    private final Map<UUID, PriceModifier>              globalModifiers = new HashMap<>();
    private final Map<UUID, Map<String, PriceModifier>> shopModifiers   = new HashMap<>();
    private final Map<UUID, Map<String, PriceModifier>> itemModifiers   = new HashMap<>();

    public PriceModifierManager(ShopModule module) {
        this.module = module;
    }


    public void setGlobalModifier(UUID player, double value, boolean isBuy) {
        PriceModifier mod = globalModifiers.computeIfAbsent(player, k -> new PriceModifier());
        if (isBuy) mod.setBuyModifier(value); else mod.setSellModifier(value);
    }

    public void setShopModifier(UUID player, String shopId, double value, boolean isBuy) {
        PriceModifier mod = shopModifiers
                .computeIfAbsent(player, k -> new HashMap<>())
                .computeIfAbsent(shopId, k -> new PriceModifier());
        if (isBuy) mod.setBuyModifier(value); else mod.setSellModifier(value);
    }

    public void setItemModifier(UUID player, String shopId, String itemId, double value, boolean isBuy) {
        String key = shopId + ":" + itemId;
        PriceModifier mod = itemModifiers
                .computeIfAbsent(player, k -> new HashMap<>())
                .computeIfAbsent(key, k -> new PriceModifier());
        if (isBuy) mod.setBuyModifier(value); else mod.setSellModifier(value);
    }


    public void resetGlobalModifier(UUID player) { globalModifiers.remove(player); }

    public void resetShopModifier(UUID player, String shopId) {
        Map<String, PriceModifier> m = shopModifiers.get(player);
        if (m != null) m.remove(shopId);
    }

    public void resetItemModifier(UUID player, String shopId, String itemId) {
        Map<String, PriceModifier> m = itemModifiers.get(player);
        if (m != null) m.remove(shopId + ":" + itemId);
    }


    public double getEffectiveBuyPrice(UUID player, ShopItem item) {
        double base = item.getBuyPrice();
        double withMod = Math.max(0, getEffectiveModifier(player, item).applyBuy(base));
        return withMod * module.getDynamicPricingManager().getBuyMultiplier(item);
    }

    public double getEffectiveSellPrice(UUID player, ShopItem item) {
        double base = item.getSellPrice();
        double withMod = Math.max(0, getEffectiveModifier(player, item).applySell(base));
        return withMod * module.getDynamicPricingManager().getSellMultiplier(item);
    }

    private PriceModifier getEffectiveModifier(UUID player, ShopItem item) {
        String key = item.getShopId() + ":" + item.getId();
        Map<String, PriceModifier> im = itemModifiers.get(player);
        if (im != null && im.containsKey(key)) return im.get(key);
        Map<String, PriceModifier> sm = shopModifiers.get(player);
        if (sm != null && sm.containsKey(item.getShopId())) return sm.get(item.getShopId());
        PriceModifier gm = globalModifiers.get(player);
        return gm != null ? gm : new PriceModifier();
    }


    public PriceModifier              getGlobalModifier(UUID player) {
        return globalModifiers.getOrDefault(player, new PriceModifier());
    }
    public Map<String, PriceModifier> getShopModifiers(UUID player) {
        return shopModifiers.getOrDefault(player, Collections.emptyMap());
    }
    public Map<String, PriceModifier> getItemModifiers(UUID player) {
        return itemModifiers.getOrDefault(player, Collections.emptyMap());
    }
}