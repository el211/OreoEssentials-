package fr.elias.oreoEssentials.modules.aliases;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AliasService}.
 *
 * <p>Uses the package-private {@code AliasService(Logger, File)} constructor so no
 * Bukkit server or {@code JavaPlugin} instance is required. Pure Java logic only.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AliasService")
class AliasServiceTest {

    private static final Logger LOG = Logger.getLogger("AliasServiceTest");

    @TempDir private Path tempDir;

    private AliasService service;

    @BeforeEach
    void setUp() {
        // Package-private constructor: no Bukkit required
        service = new AliasService(LOG, tempDir.toFile());
    }

    // ─── Registry operations ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Registry")
    class RegistryTest {

        @Test
        @DisplayName("get() returns null for unknown alias")
        void get_unknown_returnsNull() {
            assertNull(service.get("nonexistent"));
        }

        @Test
        @DisplayName("get() is case-insensitive")
        void get_caseInsensitive() {
            AliasService.AliasDef def = defWithName("MyAlias");
            service.put(def);

            assertSame(def, service.get("MYALIAS"));
            assertSame(def, service.get("myalias"));
            assertSame(def, service.get("MyAlias"));
        }

        @Test
        @DisplayName("put() followed by get() returns the same definition")
        void put_thenGet_returnsDef() {
            AliasService.AliasDef def = defWithName("test");
            service.put(def);
            assertSame(def, service.get("test"));
        }

        @Test
        @DisplayName("exists() returns true for registered alias")
        void exists_registeredAlias_returnsTrue() {
            service.put(defWithName("warp"));
            assertTrue(service.exists("warp"));
        }

        @Test
        @DisplayName("exists() returns false for unregistered alias")
        void exists_unknownAlias_returnsFalse() {
            assertFalse(service.exists("ghost"));
        }

        @Test
        @DisplayName("remove() makes the alias unreachable")
        void remove_aliasBecomesUnreachable() {
            service.put(defWithName("tmp"));
            assertTrue(service.exists("tmp"));

            service.remove("tmp");

            assertFalse(service.exists("tmp"));
            assertNull(service.get("tmp"));
        }

        @Test
        @DisplayName("put(null) does not throw")
        void put_null_doesNotThrow() {
            assertDoesNotThrow(() -> service.put(null));
        }

        @Test
        @DisplayName("put(def with null name) does not throw")
        void put_nullName_doesNotThrow() {
            AliasService.AliasDef nameless = new AliasService.AliasDef();
            nameless.name = null;
            assertDoesNotThrow(() -> service.put(nameless));
        }

        @Test
        @DisplayName("all() returns an unmodifiable view of all aliases")
        void all_returnsUnmodifiableView() {
            service.put(defWithName("a"));
            service.put(defWithName("b"));

            var all = service.all();
            assertEquals(2, all.size());
            assertThrows(UnsupportedOperationException.class,
                    () -> all.put("x", new AliasService.AliasDef()));
        }
    }

    // ─── Cooldown logic ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cooldown")
    class CooldownTest {

        @Test
        @DisplayName("First call always passes")
        void firstCall_alwaysPasses() {
            assertTrue(service.checkAndTouchCooldown("test", UUID.randomUUID(), 60));
        }

        @Test
        @DisplayName("Second call within window returns false")
        void secondCall_withinWindow_fails() {
            UUID uid = UUID.randomUUID();
            service.checkAndTouchCooldown("cmd", uid, 60);
            assertFalse(service.checkAndTouchCooldown("cmd", uid, 60));
        }

        @Test
        @DisplayName("Cooldown is per-player: different UUIDs are independent")
        void cooldown_perPlayer_independent() {
            UUID alice = UUID.randomUUID();
            UUID bob   = UUID.randomUUID();
            service.checkAndTouchCooldown("cmd", alice, 60);
            assertTrue(service.checkAndTouchCooldown("cmd", bob, 60));
        }

        @Test
        @DisplayName("Cooldown is per-alias: different aliases are independent")
        void cooldown_perAlias_independent() {
            UUID uid = UUID.randomUUID();
            service.checkAndTouchCooldown("alpha", uid, 60);
            assertTrue(service.checkAndTouchCooldown("beta", uid, 60));
        }

        @Test
        @DisplayName("Zero-second cooldown always passes")
        void zeroCooldown_alwaysPasses() {
            UUID uid = UUID.randomUUID();
            service.checkAndTouchCooldown("cmd", uid, 0);
            assertTrue(service.checkAndTouchCooldown("cmd", uid, 0));
        }

        @Test
        @DisplayName("Null UUID (console) always passes regardless of window")
        void nullUuid_consolePasses() {
            assertTrue(service.checkAndTouchCooldown("cmd", null, 60));
        }
    }

    // ─── Per-line cooldown logic ──────────────────────────────────────────────

    @Nested
    @DisplayName("Per-line cooldown")
    class LineCooldownTest {

        @Test
        @DisplayName("First call on each line passes")
        void firstCall_eachLine_passes() {
            UUID uid = UUID.randomUUID();
            assertTrue(service.checkAndTouchLineCooldown("cmd", 0, uid, 60));
            assertTrue(service.checkAndTouchLineCooldown("cmd", 1, uid, 60));
        }

        @Test
        @DisplayName("Second call on same line within window fails")
        void secondCall_sameLine_withinWindow_fails() {
            UUID uid = UUID.randomUUID();
            service.checkAndTouchLineCooldown("cmd", 0, uid, 60);
            assertFalse(service.checkAndTouchLineCooldown("cmd", 0, uid, 60));
        }

        @Test
        @DisplayName("Cooldown is independent across line indices")
        void cooldown_independentAcrossLines() {
            UUID uid = UUID.randomUUID();
            service.checkAndTouchLineCooldown("cmd", 0, uid, 60);
            assertTrue(service.checkAndTouchLineCooldown("cmd", 1, uid, 60));
        }
    }

    // ─── Check evaluation ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("evaluateAllChecks")
    class EvaluateAllChecksTest {

        @Test
        @DisplayName("Null def returns true")
        void nullDef_returnsTrue() {
            assertTrue(service.evaluateAllChecks(mock(CommandSender.class), null));
        }

        @Test
        @DisplayName("Empty checks list always returns true")
        void emptyChecks_returnsTrue() {
            AliasService.AliasDef def = defWithName("x");
            def.checks = List.of();
            assertTrue(service.evaluateAllChecks(mock(CommandSender.class), def));
        }

        @Test
        @DisplayName("AND — all checks pass → true")
        void and_allPass_returnsTrue() {
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("a.b")).thenReturn(true);
            when(sender.hasPermission("c.d")).thenReturn(true);

            assertTrue(service.evaluateAllChecks(sender,
                    defWithChecks(AliasService.LogicType.AND, "permission:a.b", "permission:c.d")));
        }

        @Test
        @DisplayName("AND — one check fails → false")
        void and_oneFails_returnsFalse() {
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("a.b")).thenReturn(true);
            when(sender.hasPermission("c.d")).thenReturn(false);

            assertFalse(service.evaluateAllChecks(sender,
                    defWithChecks(AliasService.LogicType.AND, "permission:a.b", "permission:c.d")));
        }

        @Test
        @DisplayName("OR — one check passes → true")
        void or_onePasses_returnsTrue() {
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("a.b")).thenReturn(false);
            when(sender.hasPermission("c.d")).thenReturn(true);

            assertTrue(service.evaluateAllChecks(sender,
                    defWithChecks(AliasService.LogicType.OR, "permission:a.b", "permission:c.d")));
        }

        @Test
        @DisplayName("OR — all checks fail → false")
        void or_allFail_returnsFalse() {
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("a.b")).thenReturn(false);
            when(sender.hasPermission("c.d")).thenReturn(false);

            assertFalse(service.evaluateAllChecks(sender,
                    defWithChecks(AliasService.LogicType.OR, "permission:a.b", "permission:c.d")));
        }
    }

    // ─── evaluateSingle — permission checks ───────────────────────────────────

    @Nested
    @DisplayName("evaluateSingle — permissions")
    class EvaluateSinglePermissionTest {

        @Test
        @DisplayName("permission:node — has perm → true")
        void permission_granted_returnsTrue() {
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("oreo.admin")).thenReturn(true);
            assertTrue(service.evaluateSingle(sender, "permission:oreo.admin"));
        }

        @Test
        @DisplayName("permission:node — lacks perm → false")
        void permission_denied_returnsFalse() {
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("oreo.admin")).thenReturn(false);
            assertFalse(service.evaluateSingle(sender, "permission:oreo.admin"));
        }

        @Test
        @DisplayName("!permission:node — has perm → false (negation)")
        void negatedPermission_hasPerm_returnsFalse() {
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("oreo.vip")).thenReturn(true);
            assertFalse(service.evaluateSingle(sender, "!permission:oreo.vip"));
        }

        @Test
        @DisplayName("!permission:node — lacks perm → true (negation)")
        void negatedPermission_lacksPerm_returnsTrue() {
            CommandSender sender = mock(CommandSender.class);
            when(sender.hasPermission("oreo.vip")).thenReturn(false);
            assertTrue(service.evaluateSingle(sender, "!permission:oreo.vip"));
        }
    }

    // ─── evaluateSingle — string operators ────────────────────────────────────

    @Nested
    @DisplayName("evaluateSingle — string operators")
    class EvaluateSingleStringOpTest {

        // CommandSender (non-Player) so PAPI is never invoked.

        @Test
        @DisplayName("'hello'='hello' → true")
        void stringEquals_match() {
            assertTrue(service.evaluateSingle(mock(CommandSender.class), "'hello'='hello'"));
        }

        @Test
        @DisplayName("'hello'='world' → false")
        void stringEquals_noMatch() {
            assertFalse(service.evaluateSingle(mock(CommandSender.class), "'hello'='world'"));
        }

        @Test
        @DisplayName("'hello'!='world' → true")
        void stringNotEquals() {
            assertTrue(service.evaluateSingle(mock(CommandSender.class), "'hello'!='world'"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"'abc'<-'b'"})
        @DisplayName("contains operator (<-)")
        void containsOperator(String expr) {
            assertTrue(service.evaluateSingle(mock(CommandSender.class), expr));
        }

        @ParameterizedTest
        @ValueSource(strings = {"'hello'|-'hel'"})
        @DisplayName("starts-with operator (|-)")
        void startsWithOperator(String expr) {
            assertTrue(service.evaluateSingle(mock(CommandSender.class), expr));
        }

        @ParameterizedTest
        @ValueSource(strings = {"'hello'-|'llo'"})
        @DisplayName("ends-with operator (-|)")
        void endsWithOperator(String expr) {
            assertTrue(service.evaluateSingle(mock(CommandSender.class), expr));
        }
    }

    // ─── evaluateSingle — numeric comparisons ─────────────────────────────────

    @Nested
    @DisplayName("evaluateSingle — numeric comparisons")
    class EvaluateSingleNumericTest {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
            "'5'='5',   true",
            "'5'='6',   false",
            "'5'>='5',  true",
            "'5'>='4',  true",
            "'5'>='6',  false",
            "'5'>'4',   true",
            "'5'>'5',   false",
            "'5'<='5',  true",
            "'5'<='6',  true",
            "'5'<='4',  false",
            "'5'<'6',   true",
            "'5'<'5',   false",
            "'5'!='6',  true",
            "'5'!='5',  false",
        })
        @DisplayName("numeric comparison")
        void numericComparison(String expr, boolean expected) {
            assertEquals(expected, service.evaluateSingle(mock(CommandSender.class), expr.trim()));
        }
    }

    // ─── Check record ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Check record")
    class CheckRecordTest {

        @Test
        @DisplayName("expr() returns the value passed at construction")
        void check_exprAccessor() {
            assertEquals("permission:oreo.admin",
                    new AliasService.Check("permission:oreo.admin").expr());
        }

        @Test
        @DisplayName("Two Check records with the same expr are equal")
        void check_equality() {
            assertEquals(
                    new AliasService.Check("permission:a"),
                    new AliasService.Check("permission:a"));
        }

        @Test
        @DisplayName("Two Check records with different expr are not equal")
        void check_inequality() {
            assertNotEquals(
                    new AliasService.Check("permission:a"),
                    new AliasService.Check("permission:b"));
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static AliasService.AliasDef defWithName(String name) {
        AliasService.AliasDef def = new AliasService.AliasDef();
        def.name = name.toLowerCase();
        return def;
    }

    private static AliasService.AliasDef defWithChecks(
            AliasService.LogicType logic, String... exprs) {
        AliasService.AliasDef def = defWithName("_test");
        def.logic = logic;
        for (String expr : exprs) {
            def.checks.add(new AliasService.Check(expr));
        }
        return def;
    }
}
