package fr.elias.oreoEssentials.modules.aliases;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DynamicAliasTabCompleter}.
 *
 * <p>Because {@code Bukkit.getOnlinePlayers()} is a static method on a class that
 * requires a running server, the tab completer exposes a package-private constructor
 * that accepts a {@code Supplier<Collection<? extends Player>>} in place of the live
 * Bukkit call. Tests use this seam to inject a controlled player list without any
 * server infrastructure.</p>
 *
 * <p>{@code Command} is an abstract Bukkit class that cannot be instrumented on JDK 25
 * (byte-buddy limitation). Because {@code onTabComplete} never reads the {@code command}
 * parameter, {@code null} is passed safely. {@code CommandSender} is an interface and is
 * mocked inline where needed.</p>
 */
@DisplayName("DynamicAliasTabCompleter")
class DynamicAliasTabCompleterTest {

    private static final Logger LOG = Logger.getLogger("TabCompleterTest");

    @TempDir private Path tempDir;

    private static final String ALIAS = "testcmd";

    private AliasService service;

    @BeforeEach
    void setUp() {
        // Package-private constructor: no JavaPlugin or Bukkit server required
        service = new AliasService(LOG, tempDir.toFile());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Creates a completer with the given tab groups and a fixed online-player list. */
    @SafeVarargs
    private DynamicAliasTabCompleter completer(Collection<? extends Player> online,
                                               List<String>... tabGroups) {
        AliasService.AliasDef def = new AliasService.AliasDef();
        def.name    = ALIAS;
        def.addTabs = true;
        for (List<String> group : tabGroups) {
            def.customTabs.add(group);
        }
        service.put(def);

        return new DynamicAliasTabCompleter(service, ALIAS, () -> online);
    }

    /**
     * Simulates calling onTabComplete with the given args array.
     * {@code sender} and {@code command} are unused by the implementation; null is safe.
     */
    private List<String> complete(DynamicAliasTabCompleter c, String... args) {
        CommandSender sender = mock(CommandSender.class);
        return c.onTabComplete(sender, null, ALIAS, args);
    }

    /** Creates a minimal mock Player with the given name. */
    private static Player mockPlayer(String name) {
        Player p = mock(Player.class);
        when(p.getName()).thenReturn(name);
        return p;
    }

    // ─── Static entries ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Static entries")
    class StaticEntries {

        @Test
        @DisplayName("Returns all entries when token is empty")
        void emptyToken_returnsAll() {
            var c = completer(List.of(), List.of("give", "take", "set"));
            assertEquals(List.of("give", "set", "take"), complete(c, ""));
        }

        @Test
        @DisplayName("Filters entries by prefix")
        void prefixFilter_returnsMatching() {
            var c = completer(List.of(), List.of("give", "get", "take"));
            assertEquals(List.of("get", "give"), complete(c, "g"));
        }

        @Test
        @DisplayName("Prefix filtering is case-insensitive")
        void prefixFilter_caseInsensitive() {
            var c = completer(List.of(), List.of("Give", "TAKE", "get"));
            assertEquals(List.of("get", "Give"), complete(c, "G"));
        }

        @Test
        @DisplayName("Returns empty when no entries match the prefix")
        void noMatch_returnsEmpty() {
            var c = completer(List.of(), List.of("give", "take"));
            assertTrue(complete(c, "xyz").isEmpty());
        }

        @Test
        @DisplayName("Results are sorted case-insensitively")
        void results_sortedCaseInsensitive() {
            var c = completer(List.of(), List.of("Zeta", "alpha", "Beta"));
            assertEquals(List.of("alpha", "Beta", "Zeta"), complete(c, ""));
        }

        @Test
        @DisplayName("Null entries in the group are silently ignored")
        void nullEntries_ignored() {
            AliasService.AliasDef def = new AliasService.AliasDef();
            def.name    = ALIAS;
            def.addTabs = true;
            // Manually build a group with a null entry (can't happen from config
            // but is a safety guarantee from the code)
            List<String> group = new java.util.ArrayList<>();
            group.add("valid");
            group.add(null);
            group.add("also-valid");
            def.customTabs.add(group);
            service.put(def);

            DynamicAliasTabCompleter c = new DynamicAliasTabCompleter(service, ALIAS, List::of);
            List<String> result = complete(c, "");
            assertEquals(List.of("also-valid", "valid"), result);
        }
    }

    // ─── [players] dynamic token ──────────────────────────────────────────────

    @Nested
    @DisplayName("[players] token")
    class PlayersToken {

        @Test
        @DisplayName("Expands to all online player names")
        void expandsToOnlineNames() {
            Collection<Player> online = List.of(
                    mockPlayer("Alice"), mockPlayer("Bob"), mockPlayer("Charlie")
            );
            var c = completer(online, List.of("[players]"));
            assertEquals(List.of("Alice", "Bob", "Charlie"), complete(c, ""));
        }

        @Test
        @DisplayName("Expands to empty list when no players are online")
        void noOnlinePlayers_returnsEmpty() {
            var c = completer(List.of(), List.of("[players]"));
            assertTrue(complete(c, "").isEmpty());
        }

        @Test
        @DisplayName("Filters by prefix")
        void prefixFilter_appliedToPlayerNames() {
            Collection<Player> online = List.of(
                    mockPlayer("blockSapphire"), mockPlayer("Galaxy_da"), mockPlayer("BlackWolf")
            );
            var c = completer(online, List.of("[players]"));
            // "bl" should match blockSapphire and BlackWolf (case-insensitive)
            assertEquals(List.of("BlackWolf", "blockSapphire"), complete(c, "bl"));
        }

        @Test
        @DisplayName("Mixed [players] and static values are merged")
        void mixedWithStatic() {
            // The YAML loader splits "  [players],console" into ["[players]", "console"].
            // Pass the already-split list to match real data flow.
            Collection<Player> online = List.of(mockPlayer("Alice"), mockPlayer("Bob"));
            var c = completer(online, List.of("[players]", "console"));
            assertEquals(List.of("Alice", "Bob", "console"), complete(c, ""));
        }

        @Test
        @DisplayName("Duplicate names between online players and static are deduplicated")
        void deduplication_playerNameMatchesStaticEntry() {
            // "Alice" is both online and in the static list — after YAML split the group
            // contains ["[players]", "Alice", "extra"].
            Collection<Player> online = List.of(mockPlayer("Alice"), mockPlayer("Bob"));
            var c = completer(online, List.of("[players]", "Alice", "extra"));
            // Alice must appear exactly once
            List<String> result = complete(c, "");
            assertEquals(1, result.stream().filter("Alice"::equalsIgnoreCase).count());
            assertTrue(result.contains("Bob"));
            assertTrue(result.contains("extra"));
        }

        @Test
        @DisplayName("Results from [players] are sorted case-insensitively")
        void resultsSortedCaseInsensitive() {
            Collection<Player> online = List.of(
                    mockPlayer("Zack"), mockPlayer("alice"), mockPlayer("Mike")
            );
            var c = completer(online, List.of("[players]"));
            assertEquals(List.of("alice", "Mike", "Zack"), complete(c, ""));
        }

        @Test
        @DisplayName("[players] token is case-insensitive in the config value")
        void tokenIsCaseInsensitive() {
            Collection<Player> online = List.of(mockPlayer("Alice"));
            var c = completer(online, List.of("[PLAYERS]"));
            assertEquals(List.of("Alice"), complete(c, ""));
        }
    }

    // ─── [playerName] legacy alias ────────────────────────────────────────────

    @Nested
    @DisplayName("[playerName] legacy alias in custom-tabs")
    class PlayerNameToken {

        @Test
        @DisplayName("[playerName] in custom-tabs expands like [players]")
        void playerNameToken_expandsToOnlinePlayers() {
            Collection<Player> online = List.of(mockPlayer("Elias"), mockPlayer("Galaxy"));
            var c = completer(online, List.of("[playerName]"));
            assertEquals(List.of("Elias", "Galaxy"), complete(c, ""));
        }

        @Test
        @DisplayName("[playerName] token is case-insensitive")
        void playerNameToken_caseInsensitive() {
            Collection<Player> online = List.of(mockPlayer("Elias"));
            var c = completer(online, List.of("[PLAYERNAME]"));
            assertEquals(List.of("Elias"), complete(c, ""));
        }
    }

    // ─── Multi-argument completion ────────────────────────────────────────────

    @Nested
    @DisplayName("Multi-argument completion")
    class MultiArgCompletion {

        @Test
        @DisplayName("First arg group used for first argument")
        void firstArgGroup() {
            var c = completer(List.of(), List.of("give", "take"), List.of("[players]"));
            assertEquals(List.of("give", "take"), complete(c, ""));
        }

        @Test
        @DisplayName("Second arg group used for second argument")
        void secondArgGroup() {
            Collection<Player> online = List.of(mockPlayer("Alice"));
            var c = completer(online, List.of("give", "take"), List.of("[players]"));
            // args[0] = "give", args[1] = "" (current token)
            assertEquals(List.of("Alice"), complete(c, "give", ""));
        }

        @Test
        @DisplayName("Returns empty when arg index exceeds defined tab groups")
        void argIndexBeyondGroups_returnsEmpty() {
            var c = completer(List.of(), List.of("give"));
            // args has 3 elements, but only 1 group defined
            assertTrue(complete(c, "give", "diamond", "").isEmpty());
        }
    }

    // ─── Guard conditions ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Guard conditions")
    class GuardConditions {

        @Test
        @DisplayName("Returns empty when def not found")
        void defNotFound_returnsEmpty() {
            DynamicAliasTabCompleter c = new DynamicAliasTabCompleter(service, "missing", List::of);
            assertTrue(complete(c, "").isEmpty());
        }

        @Test
        @DisplayName("Returns empty when addTabs is false")
        void addTabsFalse_returnsEmpty() {
            AliasService.AliasDef def = new AliasService.AliasDef();
            def.name    = ALIAS;
            def.addTabs = false;
            def.customTabs.add(List.of("give", "take"));
            service.put(def);

            DynamicAliasTabCompleter c = new DynamicAliasTabCompleter(service, ALIAS, List::of);
            assertTrue(complete(c, "").isEmpty());
        }

        @Test
        @DisplayName("Returns empty when custom-tabs group is empty")
        void emptyGroup_returnsEmpty() {
            AliasService.AliasDef def = new AliasService.AliasDef();
            def.name    = ALIAS;
            def.addTabs = true;
            def.customTabs.add(List.of());
            service.put(def);

            DynamicAliasTabCompleter c = new DynamicAliasTabCompleter(service, ALIAS, List::of);
            assertTrue(complete(c, "").isEmpty());
        }
    }
}
