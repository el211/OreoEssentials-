package fr.elias.oreoEssentials.modules.skin;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URL;

/**
 * Utility class for handling player skins and profiles.
 * Uses Paper's PlayerProfile API.
 */
public final class SkinUtil {

    private SkinUtil() {
    }

    /**
     * Gets the PlayerProfile of an online player by exact name match.
     *
     * @param name The exact player name (case-insensitive)
     * @return PlayerProfile if player is online, null otherwise
     */
    public static PlayerProfile onlineProfileOf(String name) {
        try {
            Player p = Bukkit.getPlayerExact(name);
            if (p != null) {
                SkinDebug.log("onlineProfileOf: found online player " + name);
                return p.getPlayerProfile();
            }
            SkinDebug.log("onlineProfileOf: " + name + " not online");
            return null;
        } catch (Throwable t) {
            SkinDebug.log("onlineProfileOf threw: " + t.getMessage());
            return null;
        }
    }

    /**
     * Copies skin and cape textures from source profile to destination profile.
     *
     * @param src Source PlayerProfile to copy from
     * @param dst Destination PlayerProfile to copy to
     */
    public static void copyTextures(PlayerProfile src, PlayerProfile dst) {
        if (src == null || dst == null) {
            SkinDebug.log("copyTextures: src/dst null");
            return;
        }

        try {
            SkinDebug.log("=== COPY TEXTURES DEBUG ===");
            SkinDebug.log("Source profile: " + src.getName());
            SkinDebug.log("Source has textures: " + src.hasTextures());

            if (src.hasTextures()) {
                URL skinUrl = src.getTextures().getSkin();
                URL capeUrl = src.getTextures().getCape();

                SkinDebug.log("Source skin URL: " + skinUrl);
                SkinDebug.log("Source cape URL: " + capeUrl);

                if (skinUrl != null) {
                    dst.setTextures(src.getTextures());
                    SkinDebug.log("✓ Copied textures to destination");
                } else {
                    SkinDebug.log("✗ Source skin URL is null!");
                }
            } else {
                SkinDebug.log("✗ Source profile has no textures!");
            }

            SkinDebug.log("Destination profile: " + dst.getName());
            SkinDebug.log("Destination has textures: " + dst.hasTextures());
            if (dst.hasTextures()) {
                SkinDebug.log("Destination skin URL: " + dst.getTextures().getSkin());
            }
            SkinDebug.log("=========================");

        } catch (Throwable t) {
            SkinDebug.log("copyTextures threw: " + t.getMessage());
            t.printStackTrace();
        }
    }

    /**
     * Attempts to set the name of a PlayerProfile.
     * Note: This changes the profile's internal name, not the player's display name.
     * This may not be supported on all Paper builds and has limited practical use.
     *
     * @param profile The profile to modify
     * @param name The new name
     * @return true if successful, false otherwise
     */
    public static boolean setProfileName(PlayerProfile profile, String name) {
        if (profile == null || name == null || name.isEmpty()) {
            SkinDebug.log("setProfileName: invalid input (profile=" + profile + ", name=" + name + ")");
            return false;
        }

        try {
            profile.setName(name);
            SkinDebug.log("setProfileName: set to " + name);
            return true;
        } catch (NoSuchMethodError e) {
            SkinDebug.log("setProfileName: setName() not available on this Paper build");
            return false;
        } catch (Throwable t) {
            SkinDebug.log("setProfileName: threw " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return false;
        }
    }

    /**
     * Applies a PlayerProfile to a player, updating their skin and textures.
     * Requires Paper 1.18.2+.
     *
     * @param player The player to apply the profile to
     * @param newProfile The profile containing the new skin/textures
     * @return true if successfully applied, false otherwise
     */
    public static boolean applyProfile(Player player, PlayerProfile newProfile) {
        if (player == null || newProfile == null) {
            SkinDebug.log("applyProfile: player or profile is null");
            return false;
        }

        try {
            player.setPlayerProfile(newProfile);
            SkinDebug.log("applyProfile: Player#setPlayerProfile succeeded for " + player.getName());
            return true;

        } catch (NoSuchMethodError e) {
            SkinDebug.log("applyProfile: setPlayerProfile not available on this server version");
            SkinDebug.log("applyProfile: Please upgrade to Paper 1.18.2 or newer");
            return false;

        } catch (Throwable t) {
            SkinDebug.log("applyProfile: threw " + t.getClass().getSimpleName() + ": " + t.getMessage());
            t.printStackTrace();
            return false;
        }
    }
}