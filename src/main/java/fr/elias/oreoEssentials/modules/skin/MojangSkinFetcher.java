package fr.elias.oreoEssentials.modules.skin;

import com.destroystokyo.paper.profile.PlayerProfile;  // ← PAPER IMPORT
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class MojangSkinFetcher {

    private MojangSkinFetcher() {}

    public static UUID fetchUuid(String username) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();

            if (responseCode == 429) {
                SkinDebug.log("⚠ Mojang API rate limit hit! Wait a minute.");
                return null;
            }

            if (responseCode != 200) {
                SkinDebug.log("MojangAPI: UUID fetch failed for " + username + " (HTTP " + responseCode + ")");
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
            String id = json.get("id").getAsString();

            String formatted = id.replaceFirst(
                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                    "$1-$2-$3-$4-$5"
            );

            UUID uuid = UUID.fromString(formatted);
            SkinDebug.log("MojangAPI: UUID for " + username + " = " + uuid);
            return uuid;

        } catch (Exception e) {
            SkinDebug.log("MojangAPI: Error fetching UUID for " + username + ": " + e.getMessage());
            return null;
        }
    }

    public static PlayerProfile fetchProfileWithTextures(UUID uuid, String name) {
        try {
            PlayerProfile profile = Bukkit.createProfile(uuid, name);

            CompletableFuture<PlayerProfile> future = profile.update();

            PlayerProfile updated = future.get(10, TimeUnit.SECONDS);

            if (updated == null || !updated.hasTextures()) {
                SkinDebug.log("MojangAPI: Profile update returned no textures for " + name);
                return null;
            }

            SkinDebug.log("MojangAPI: Successfully fetched profile for " + name);
            return updated;

        } catch (java.util.concurrent.TimeoutException e) {
            SkinDebug.log("MojangAPI: Timeout fetching profile for " + name);
            return null;
        } catch (Exception e) {
            SkinDebug.log("MojangAPI: Error fetching profile: " + e.getMessage());
            return null;
        }
    }

    public static void fetchProfileAsync(UUID uuid, String name,
                                         java.util.function.Consumer<PlayerProfile> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(
                fr.elias.oreoEssentials.OreoEssentials.get(),
                () -> {
                    PlayerProfile profile = fetchProfileWithTextures(uuid, name);

                    Bukkit.getScheduler().runTask(
                            fr.elias.oreoEssentials.OreoEssentials.get(),
                            () -> callback.accept(profile)
                    );
                }
        );
    }
}