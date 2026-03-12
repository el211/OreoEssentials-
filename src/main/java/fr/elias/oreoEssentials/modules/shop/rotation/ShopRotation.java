package fr.elias.oreoEssentials.modules.shop.rotation;

import java.util.Set;

/**
 * The active rotation state for one shop:
 * which item IDs are shown this period and when the next reset fires.
 */
public final class ShopRotation {

    private final String      shopId;
    private final Set<String> activeItemIds;  // ordered, no duplicates
    private final long        nextResetMs;    // epoch milliseconds

    public ShopRotation(String shopId, Set<String> activeItemIds, long nextResetMs) {
        this.shopId        = shopId;
        this.activeItemIds = activeItemIds;
        this.nextResetMs   = nextResetMs;
    }

    public String      getShopId()        { return shopId; }
    public Set<String> getActiveItemIds() { return activeItemIds; }
    public long        getNextResetMs()   { return nextResetMs; }

    /** True when the current wall-clock time has passed the stored reset instant. */
    public boolean isExpired() {
        return System.currentTimeMillis() >= nextResetMs;
    }
}
