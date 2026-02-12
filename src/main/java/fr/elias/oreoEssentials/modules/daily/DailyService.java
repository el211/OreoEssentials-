package fr.elias.oreoEssentials.modules.daily;

import fr.elias.oreoEssentials.OreoEssentials;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;


public final class DailyService {

    private final OreoEssentials plugin;
    private final DailyConfig cfg;
    private final DailyStorage store;
    private final RewardsConfig rewards;

    private volatile boolean featureEnabled;

    public DailyService(OreoEssentials plugin, DailyConfig cfg, DailyStorage store, RewardsConfig rewards) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.store = store;
        this.rewards = rewards;
        this.featureEnabled = cfg.enabled; // respect config at boot
    }


    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.systemDefault());
    }


    public boolean canClaimToday(Player p) {
        if (!featureEnabled) return false;

        DailyStorage.Record rec = store.ensure(p.getUniqueId(), p.getName());
        LocalDate last = rec.getLastClaimDate();
        if (last == null) return true;

        // Current implementation: both modes behave as calendar-day gating.
        // (We only store LocalDate (epochDay), not time-of-day.)
        return !today().isEqual(last);
    }


    public int getStreak(UUID u) {
        DailyStorage.Record r = store.get(u);
        return (r == null) ? 0 : r.getStreak();
    }


    public int nextDayIndex(int currentStreak) {
        int max = Math.max(1, rewards.maxDay());
        if (cfg.resetWhenStreakCompleted) {
            // cycle 1..max, then wrap
            int next = currentStreak + 1;
            return ((next - 1) % max) + 1;
        } else {
            // cap at max; keep awarding "Day max"
            return Math.min(currentStreak + 1, max);
        }
    }



    public boolean claim(Player p) {
        if (!featureEnabled) {
            p.sendMessage(color(cfg.prefix + " &cDaily Rewards is currently disabled."));
            return false;
        }

        DailyStorage.Record r = store.ensure(p.getUniqueId(), p.getName());
        LocalDate t = today();
        LocalDate last = r.getLastClaimDate();

        // Already claimed today
        if (last != null && t.isEqual(last)) return false;

        // Compute new streak based on gap logic
        int newStreak;
        if (last == null) {
            newStreak = 1;
        } else {
            long gapDays = Duration.between(last.atStartOfDay(), t.atStartOfDay()).toDays();
            if (gapDays == 1) {
                newStreak = r.getStreak() + 1;
            } else if (cfg.pauseStreakWhenMissed) {
                newStreak = r.getStreak();
            } else if (cfg.skipMissedDays) {
                newStreak = r.getStreak() + 1;
            } else {
                newStreak = 1;
            }
        }

        // Determine which reward day to grant (based on streak BEFORE update)
        int dayToAward = nextDayIndex(r.getStreak());
        RewardsConfig.DayDef def = rewards.day(dayToAward);

        // Persist first (upsert)
        store.updateOnClaim(p.getUniqueId(), p.getName(), newStreak, t);

        // Execute reward commands (if configured)
        if (def != null && def.commands != null) {
            for (String raw : def.commands) {
                String cmd = (raw == null ? "" : raw).trim();
                if (cmd.isEmpty() || cmd.equalsIgnoreCase("\"\"")) continue;
                cmd = cmd.replace("<playerName>", p.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
            if (def.message != null && !def.message.isEmpty()) {
                p.sendMessage(color(def.message));
            }
        }

        p.sendMessage(color(cfg.prefix + " &aClaimed &fDay " + dayToAward + " &areward! Streak: &f" + newStreak));
        return true;
    }



    public boolean isEnabled() {
        return featureEnabled;
    }

    public void setEnabled(boolean v) {
        this.featureEnabled = v;
    }


    public boolean toggleEnabled() {
        this.featureEnabled = !this.featureEnabled;
        return this.featureEnabled;
    }
}