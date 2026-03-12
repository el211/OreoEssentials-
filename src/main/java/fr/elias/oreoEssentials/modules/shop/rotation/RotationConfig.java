package fr.elias.oreoEssentials.modules.shop.rotation;

/**
 * Immutable rotation settings parsed from a shop's YAML "rotating:" section.
 */
public final class RotationConfig {

    private final int    displayCount;
    private final String resetTime;   // "HH:mm" in the configured timezone
    private final String timezone;    // IANA timezone ID, e.g. "Europe/Paris"

    public RotationConfig(int displayCount, String resetTime, String timezone) {
        this.displayCount = displayCount;
        this.resetTime    = resetTime;
        this.timezone     = timezone;
    }

    public int    getDisplayCount() { return displayCount; }
    public String getResetTime()    { return resetTime; }
    public String getTimezone()     { return timezone; }
}
