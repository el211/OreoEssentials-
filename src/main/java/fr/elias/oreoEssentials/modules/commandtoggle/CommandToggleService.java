package fr.elias.oreoEssentials.modules.commandtoggle;

import fr.elias.oreoEssentials.util.OreScheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Applies commands-toggle.yml to Bukkit's runtime CommandMap.
 *
 * Safety rules:
 * - Only commands positively identified as OreoEssentials-owned are
 *   disabled/restored or used as alias targets.
 * - Existing commands are never overwritten by configured aliases.
 * - Config/module-only keys with no runtime Oreo command are skipped quietly.
 * - This service never changes CommandManager / CommandsAPI registration.
 * - Dynamic aliases.yml commands are excluded from primary-command matching.
 * - Configured aliases use forwarding wrapper commands so CommandsAPI receives
 *   the canonical primary label instead of the alias label.
 */
public class CommandToggleService {

    private static final long SECOND_PASS_DELAY_TICKS = 1L;

    private final JavaPlugin plugin;
    private final CommandToggleConfig config;

    /** Primary command -> exact CommandMap bindings removed by this service. */
    private final Map<String, DisabledCommandState> disabledCommands = new LinkedHashMap<>();

    /** CommandMap keys inserted by this service for commands-toggle aliases. */
    private final Map<String, Command> managedAliasBindings = new LinkedHashMap<>();

    /** Invalidates an older delayed pass after a newer reload/apply cycle. */
    private long applyGeneration = 0L;

    public CommandToggleService(JavaPlugin plugin, CommandToggleConfig config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Apply immediately and once more one global tick later.
     * The retry is kept because optional modules can finish registering commands
     * later during OreoEssentials startup.
     */
    public void applyToggles() {
        final long generation = ++applyGeneration;

        ApplyStats initial = applyTogglesInternal();
        if (initial != null) {
            plugin.getLogger().info(
                    "[CommandToggle] Initial pass: "
                            + initial.disabled + " newly disabled, "
                            + initial.restored + " restored, "
                            + initial.aliasesRegistered + " alias(es) registered"
                            + formatCollisionSuffix(initial.aliasCollisions)
            );
        }

        scheduleSecondPass(generation);
    }

    private void scheduleSecondPass(long generation) {
        try {
            OreScheduler.runLater(plugin, () -> {
                if (generation != applyGeneration || !plugin.isEnabled()) {
                    return;
                }

                ApplyStats finalStats = applyTogglesInternal();
                if (finalStats == null) return;

                plugin.getLogger().info(
                        "[CommandToggle] Final pass: "
                                + finalStats.disabled + " newly disabled, "
                                + finalStats.restored + " restored, "
                                + finalStats.aliasesRegistered + " alias(es) registered"
                                + formatCollisionSuffix(finalStats.aliasCollisions)
                );

                if (!finalStats.aliasCollisions.isEmpty()) {
                    plugin.getLogger().warning(
                            "[CommandToggle] Skipped aliases already owned by another command: "
                                    + String.join(", ", finalStats.aliasCollisions)
                    );
                }
            }, SECOND_PASS_DELAY_TICKS);

        } catch (Throwable schedulerFailure) {
            if (generation == applyGeneration && plugin.isEnabled()) {
                ApplyStats finalStats = applyTogglesInternal();
                if (finalStats != null && !finalStats.aliasCollisions.isEmpty()) {
                    plugin.getLogger().warning(
                            "[CommandToggle] Skipped aliases already owned by another command: "
                                    + String.join(", ", finalStats.aliasCollisions)
                    );
                }
            }
        }
    }

    private ApplyStats applyTogglesInternal() {
        try {
            CommandMap commandMap = getCommandMap();
            if (commandMap == null) {
                plugin.getLogger().severe("[CommandToggle] Could not access CommandMap.");
                return null;
            }

            Map<String, Command> knownCommands = getKnownCommands(commandMap);
            if (knownCommands == null) {
                plugin.getLogger().severe("[CommandToggle] Could not access knownCommands.");
                return null;
            }

            Map<String, CommandToggleConfig.CommandToggleEntry> entries =
                    config.getAllCommands();

            // If a previously-disabled config entry was deleted on reload,
            // restore its captured runtime bindings instead of leaving it disabled.
            int restored = restoreNoLongerConfigured(
                    entries.keySet(),
                    knownCommands
            );

            // Rebuild only aliases that THIS service inserted previously.
            removeManagedAliasBindings(knownCommands);

            int disabled = 0;
            int aliasesRegistered = 0;
            LinkedHashSet<String> collisions = new LinkedHashSet<>();

            // 1) Disable configured Oreo primaries.
            for (CommandToggleConfig.CommandToggleEntry entry : entries.values()) {
                if (entry.isEnabled()) continue;

                if (disableCommand(entry.getName(), knownCommands)) {
                    disabled++;
                }
            }

            // 2) Restore commands previously disabled by this service.
            for (CommandToggleConfig.CommandToggleEntry entry : entries.values()) {
                if (!entry.isEnabled()) continue;

                if (restoreCommand(entry.getName(), knownCommands)) {
                    restored++;
                }
            }

            // 3) Bind aliases only to a real Oreo-owned runtime command.
            for (Map.Entry<String, String> aliasEntry :
                    config.getAliasOwners().entrySet()) {

                String alias = aliasEntry.getKey();
                String owner = aliasEntry.getValue();

                if (!config.isCommandEnabled(owner)) {
                    continue;
                }

                Command target = findPrimaryCommand(owner, knownCommands);

                if (target == null) {
                    // This is intentionally not a warning. A missing runtime
                    // target can be a module-only config key such as auctionhouse.
                    plugin.getLogger().fine(
                            "[CommandToggle] Skipping aliases for config-only/unbound entry /"
                                    + owner
                    );
                    continue;
                }

                AliasBindResult result = bindAlias(
                        alias,
                        owner,
                        target,
                        knownCommands,
                        collisions
                );

                if (result == AliasBindResult.REGISTERED) {
                    aliasesRegistered++;
                }
            }

            scheduleCommandTreeSync();

            return new ApplyStats(
                    disabled,
                    restored,
                    aliasesRegistered,
                    collisions
            );

        } catch (Throwable t) {
            plugin.getLogger().severe(
                    "[CommandToggle] Error applying toggles: " + t.getMessage()
            );
            t.printStackTrace();
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Disable / restore
    // -------------------------------------------------------------------------

    private boolean disableCommand(
            String commandName,
            Map<String, Command> knownCommands
    ) {
        String primary = CommandToggleConfig.normalizeLabel(commandName);
        if (primary.isEmpty()) return false;

        if (disabledCommands.containsKey(primary)) {
            return false;
        }

        Command target = findPrimaryCommand(primary, knownCommands);
        if (target == null) {
            // Never disable a vanilla/other-plugin command merely because a
            // config entry uses the same text label.
            return false;
        }

        LinkedHashMap<String, Command> removed = new LinkedHashMap<>();

        for (Map.Entry<String, Command> binding :
                new ArrayList<>(knownCommands.entrySet())) {

            if (binding.getValue() != target) continue;

            String key = binding.getKey();
            if (knownCommands.remove(key, target)) {
                removed.put(key, target);
                managedAliasBindings.remove(key);
            }
        }

        if (removed.isEmpty()) {
            return false;
        }

        disabledCommands.put(
                primary,
                new DisabledCommandState(target, removed)
        );

        return true;
    }

    private boolean restoreCommand(
            String commandName,
            Map<String, Command> knownCommands
    ) {
        String primary = CommandToggleConfig.normalizeLabel(commandName);
        if (primary.isEmpty()) return false;

        DisabledCommandState state = disabledCommands.remove(primary);
        if (state == null) {
            return false;
        }

        boolean restoredAny = false;

        for (Map.Entry<String, Command> binding :
                state.removedBindings.entrySet()) {

            String key = binding.getKey();
            Command existing = knownCommands.get(key);

            if (existing == null || existing == state.command) {
                knownCommands.put(key, state.command);
                restoredAny = true;
            } else {
                plugin.getLogger().warning(
                        "[CommandToggle] Could not restore '" + key
                                + "' for /" + primary
                                + " because another command now owns that label."
                );
            }
        }

        if (!restoredAny && !knownCommands.containsKey(primary)) {
            knownCommands.put(primary, state.command);
            restoredAny = true;
        }

        return restoredAny;
    }

    private int restoreNoLongerConfigured(
            Set<String> configuredPrimaries,
            Map<String, Command> knownCommands
    ) {
        int restored = 0;

        for (String disabledPrimary :
                new ArrayList<>(disabledCommands.keySet())) {

            if (configuredPrimaries.contains(disabledPrimary)) {
                continue;
            }

            if (restoreCommand(disabledPrimary, knownCommands)) {
                restored++;
            }
        }

        return restored;
    }

    // -------------------------------------------------------------------------
    // Alias binding
    // -------------------------------------------------------------------------

    private AliasBindResult bindAlias(
            String alias,
            String owner,
            Command target,
            Map<String, Command> knownCommands,
            Set<String> collisions
    ) {
        String lowerAlias = CommandToggleConfig.normalizeLabel(alias);
        String lowerOwner = CommandToggleConfig.normalizeLabel(owner);

        if (lowerAlias.isEmpty() || lowerAlias.equals(lowerOwner)) {
            return AliasBindResult.SKIPPED;
        }

        Command existing = knownCommands.get(lowerAlias);

        /*
         * IMPORTANT:
         *
         * Do NOT map the alias key directly to the target PluginCommand.
         *
         * CommandsAPI 5.1.1 dispatches using the LABEL supplied to its executor.
         * If /goto points directly to the /warp PluginCommand, Bukkit calls that
         * command with label="goto", then CommandsAPI tries to invoke a command
         * named "goto" and returns "Unknown command".
         *
         * The forwarding wrapper below forces the canonical owner label ("warp")
         * when delegating execution/tab completion to the real command.
         */
        if (existing != null && existing != target) {
            collisions.add(
                    "/" + lowerAlias + " -> /" + lowerOwner
                            + " (already owned by /" + safeCommandName(existing) + ")"
            );
            return AliasBindResult.COLLISION;
        }

        // If this label is already a real alias of the target command, leave it
        // alone. CommandsAPI already knows about aliases declared by the command.
        if (existing == target) {
            return AliasBindResult.ALREADY_BOUND;
        }

        ForwardingAliasCommand wrapper =
                new ForwardingAliasCommand(plugin, lowerAlias, lowerOwner, target);

        knownCommands.put(lowerAlias, wrapper);
        managedAliasBindings.put(lowerAlias, wrapper);

        String namespacedAlias =
                plugin.getName().toLowerCase(Locale.ROOT) + ":" + lowerAlias;

        Command namespacedExisting = knownCommands.get(namespacedAlias);

        if (namespacedExisting == null) {
            knownCommands.put(namespacedAlias, wrapper);
            managedAliasBindings.put(namespacedAlias, wrapper);

        } else if (namespacedExisting != target
                && namespacedExisting != wrapper) {

            collisions.add(
                    "/" + namespacedAlias + " -> /" + lowerOwner
                            + " (namespaced label already occupied)"
            );
        }

        return AliasBindResult.REGISTERED;
    }

    private void removeManagedAliasBindings(
            Map<String, Command> knownCommands
    ) {
        for (Map.Entry<String, Command> binding :
                new ArrayList<>(managedAliasBindings.entrySet())) {

            knownCommands.remove(binding.getKey(), binding.getValue());
        }

        managedAliasBindings.clear();
    }

    // -------------------------------------------------------------------------
    // Primary resolution
    // -------------------------------------------------------------------------

    /**
     * Finds a runtime command only when it can be positively identified as
     * belonging to OreoEssentials.
     *
     * It checks:
     * 1) direct label;
     * 2) normal OreoEssentials namespace;
     * 3) any fallback namespace ending in ":primary";
     * 4) Oreo-owned Command objects whose Command#getName() matches.
     */
    private Command findPrimaryCommand(
            String commandName,
            Map<String, Command> knownCommands
    ) {
        String primary = CommandToggleConfig.normalizeLabel(commandName);
        if (primary.isEmpty()) return null;

        Command direct = knownCommands.get(primary);
        if (isEligibleOreoCommand(direct)) {
            return direct;
        }

        String normalNamespace =
                plugin.getName().toLowerCase(Locale.ROOT) + ":" + primary;

        Command namespaced = knownCommands.get(normalNamespace);
        if (isEligibleOreoCommand(namespaced)) {
            return namespaced;
        }

        for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;

            String normalizedKey = key.toLowerCase(Locale.ROOT);
            if (!normalizedKey.endsWith(":" + primary)) continue;

            Command candidate = entry.getValue();
            if (isEligibleOreoCommand(candidate)) {
                return candidate;
            }
        }

        for (Command candidate :
                new LinkedHashSet<>(knownCommands.values())) {

            if (!isEligibleOreoCommand(candidate)) continue;

            String name = candidate.getName();
            if (name != null && name.equalsIgnoreCase(primary)) {
                return candidate;
            }
        }

        return null;
    }

    private boolean isEligibleOreoCommand(Command command) {
        return command != null
                && isOwnedByThisPlugin(command)
                && !isCustomAliasMacro(command);
    }

    private boolean isOwnedByThisPlugin(Command command) {
        if (command == null) return false;

        if (command instanceof PluginIdentifiableCommand identifiable) {
            try {
                return identifiable.getPlugin() == plugin;
            } catch (Throwable ignored) {
            }
        }

        // registerLegacy(...) creates anonymous Command subclasses under
        // OreoEssentials' own package.
        String className = command.getClass().getName();
        if (className.toLowerCase(Locale.ROOT)
                .startsWith("fr.elias.oreoessentials.")) {
            return true;
        }

        try {
            Method getPlugin = command.getClass().getMethod("getPlugin");
            Object owner = getPlugin.invoke(command);
            return owner == plugin;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isCustomAliasMacro(Command command) {
        String className = command.getClass().getName();

        return className.contains(".modules.aliases.DynamicAliasRegistry$")
                || className.endsWith(
                "DynamicAliasRegistry$InternalDynamicCommand"
        );
    }

    private String safeCommandName(Command command) {
        if (command == null
                || command.getName() == null
                || command.getName().isBlank()) {
            return "unknown";
        }

        return command.getName();
    }

    /**
     * Bukkit alias wrapper for CommandsAPI-backed commands.
     *
     * It deliberately ignores the alias label received from Bukkit and delegates
     * with the canonical command label. This is required because CommandsAPI
     * resolves commands from the label passed to its executor.
     */
    private static final class ForwardingAliasCommand
            extends Command
            implements PluginIdentifiableCommand {

        private final JavaPlugin plugin;
        private final String canonicalLabel;
        private final Command target;

        private ForwardingAliasCommand(
                JavaPlugin plugin,
                String alias,
                String canonicalLabel,
                Command target
        ) {
            super(alias);

            this.plugin = plugin;
            this.canonicalLabel = canonicalLabel;
            this.target = target;

            String description = target.getDescription();
            if (description != null && !description.isBlank()) {
                setDescription(description);
            }

            String usage = target.getUsage();
            if (usage != null && !usage.isBlank()) {
                setUsage(usage);
            }

            String permission = target.getPermission();
            if (permission != null && !permission.isBlank()) {
                setPermission(permission);
            }
        }

        @Override
        public Plugin getPlugin() {
            return plugin;
        }

        @Override
        public boolean execute(
                org.bukkit.command.CommandSender sender,
                String commandLabel,
                String[] args
        ) {
            // Permission mirrors the real target when Bukkit exposes one.
            if (!testPermission(sender)) {
                return true;
            }

            return target.execute(sender, canonicalLabel, args);
        }

        @Override
        public List<String> tabComplete(
                org.bukkit.command.CommandSender sender,
                String alias,
                String[] args
        ) throws IllegalArgumentException {

            if (!testPermissionSilent(sender)) {
                return Collections.emptyList();
            }

            List<String> result =
                    target.tabComplete(sender, canonicalLabel, args);

            return result != null
                    ? result
                    : Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Client command-tree sync
    // -------------------------------------------------------------------------

    private void scheduleCommandTreeSync() {
        try {
            OreScheduler.run(plugin, this::syncCommandTreeNow);
        } catch (Throwable ignored) {
            syncCommandTreeNow();
        }
    }

    private void syncCommandTreeNow() {
        Object server = Bukkit.getServer();

        try {
            Method method;

            try {
                method = server.getClass().getMethod("syncCommands");
            } catch (NoSuchMethodException ex) {
                method = server.getClass().getDeclaredMethod("syncCommands");
                method.setAccessible(true);
            }

            method.invoke(server);
            return;

        } catch (Throwable ignored) {
            // Folia/Paper fallback below.
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                OreScheduler.runForEntity(
                        plugin,
                        player,
                        player::updateCommands
                );
            } catch (Throwable t) {
                plugin.getLogger().fine(
                        "[CommandToggle] Could not refresh command tree for "
                                + player.getName() + ": " + t.getMessage()
                );
            }
        }
    }

    // -------------------------------------------------------------------------
    // CommandMap access
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Command> getKnownCommands(CommandMap commandMap) {
        if (commandMap == null) return null;

        try {
            if (commandMap instanceof SimpleCommandMap) {
                Field field =
                        SimpleCommandMap.class.getDeclaredField("knownCommands");
                field.setAccessible(true);

                Object result = field.get(commandMap);
                if (result instanceof Map<?, ?>) {
                    return (Map<String, Command>) result;
                }
            }

            Class<?> type = commandMap.getClass();

            while (type != null) {
                try {
                    Field field = type.getDeclaredField("knownCommands");
                    field.setAccessible(true);

                    Object result = field.get(commandMap);
                    if (result instanceof Map<?, ?>) {
                        return (Map<String, Command>) result;
                    }

                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }

        } catch (Throwable t) {
            plugin.getLogger().severe(
                    "[CommandToggle] Failed to access knownCommands: "
                            + t.getMessage()
            );
        }

        return null;
    }

    private CommandMap getCommandMap() {
        Object server = Bukkit.getServer();

        try {
            Method method = server.getClass().getMethod("getCommandMap");
            Object result = method.invoke(server);

            if (result instanceof CommandMap map) {
                return map;
            }
        } catch (Throwable ignored) {
        }

        Class<?> type = server.getClass();

        while (type != null) {
            try {
                Field field = type.getDeclaredField("commandMap");
                field.setAccessible(true);

                Object result = field.get(server);
                if (result instanceof CommandMap map) {
                    return map;
                }

                break;

            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();

            } catch (Throwable t) {
                plugin.getLogger().severe(
                        "[CommandToggle] Failed to access CommandMap: "
                                + t.getMessage()
                );
                break;
            }
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Reload
    // -------------------------------------------------------------------------

    /**
     * Module callbacks run before runtime aliases/toggles are rebuilt because a
     * callback may register/unregister its module's own commands.
     */
    public void reload() {
        config.reload();
        config.fireAllCallbacks();
        applyToggles();
    }

    private static String formatCollisionSuffix(Set<String> collisions) {
        return collisions.isEmpty()
                ? ""
                : ", " + collisions.size() + " alias collision(s)";
    }

    private record DisabledCommandState(
            Command command,
            Map<String, Command> removedBindings
    ) {}

    private record ApplyStats(
            int disabled,
            int restored,
            int aliasesRegistered,
            Set<String> aliasCollisions
    ) {}

    private enum AliasBindResult {
        REGISTERED,
        ALREADY_BOUND,
        COLLISION,
        SKIPPED
    }
}
