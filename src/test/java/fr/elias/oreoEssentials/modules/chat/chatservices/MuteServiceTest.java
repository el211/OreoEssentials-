package fr.elias.oreoEssentials.modules.chat.chatservices;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MuteService}.
 *
 * <p>Uses the package-private {@code MuteService(Logger, File)} constructor so that
 * no Bukkit server or {@code JavaPlugin} instance is required.  The Folia-based
 * {@code OreScheduler.runAsyncTimer()} call is skipped in this constructor — time is
 * driven explicitly through the test data (past/future epoch values).</p>
 */
@DisplayName("MuteService")
class MuteServiceTest {

    private static final Logger LOG = Logger.getLogger("MuteServiceTest");

    @TempDir
    private Path tempDir;

    private MuteService service;

    @BeforeEach
    void setUp() {
        service = new MuteService(LOG, tempDir.toFile());
    }

    // ─── MuteData inner class ─────────────────────────────────────────────────

    @Nested
    @DisplayName("MuteData")
    class MuteDataTest {

        @Test
        @DisplayName("expired() returns false for permanent mute (until <= 0)")
        void permanent_neverExpires() {
            var md = new MuteService.MuteData(UUID.randomUUID(), 0L, "spam", "admin");
            assertFalse(md.expired());
        }

        @Test
        @DisplayName("expired() returns false when until is in the future")
        void futureUntil_notExpired() {
            long future = System.currentTimeMillis() + 60_000L;
            var md = new MuteService.MuteData(UUID.randomUUID(), future, "caps", "admin");
            assertFalse(md.expired());
        }

        @Test
        @DisplayName("expired() returns true when until is in the past")
        void pastUntil_expired() {
            long past = System.currentTimeMillis() - 1_000L;
            var md = new MuteService.MuteData(UUID.randomUUID(), past, "flaming", "admin");
            assertTrue(md.expired());
        }

        @Test
        @DisplayName("remainingMs() returns -1 for permanent mutes")
        void permanent_remainingMs_isNegativeOne() {
            var md = new MuteService.MuteData(UUID.randomUUID(), 0L, "", "");
            assertEquals(-1L, md.remainingMs());
        }

        @Test
        @DisplayName("remainingMs() is positive for future mute")
        void future_remainingMs_isPositive() {
            long future = System.currentTimeMillis() + 60_000L;
            var md = new MuteService.MuteData(UUID.randomUUID(), future, "", "");
            assertTrue(md.remainingMs() > 0);
        }

        @Test
        @DisplayName("remainingMs() returns 0 for already-expired mute (clamped)")
        void expired_remainingMs_isZero() {
            long past = System.currentTimeMillis() - 5_000L;
            var md = new MuteService.MuteData(UUID.randomUUID(), past, "", "");
            assertEquals(0L, md.remainingMs());
        }

        @Test
        @DisplayName("null reason and by are normalised to empty strings")
        void nullReasonAndBy_normalisedToEmpty() {
            var md = new MuteService.MuteData(UUID.randomUUID(), 0L, null, null);
            assertEquals("", md.reason);
            assertEquals("", md.by);
        }
    }

    // ─── parseDurationToMillis ────────────────────────────────────────────────

    @Nested
    @DisplayName("parseDurationToMillis()")
    class ParseDurationTest {

        @ParameterizedTest(name = "\"{0}\" → {1} ms")
        @CsvSource({
            "30s,    30000",
            "10m,    600000",
            "2h,     7200000",
            "1d,     86400000",
            "120,    120000",   // plain number treated as seconds
            "1S,     1000",     // case-insensitive suffix
            "5M,     300000",
        })
        @DisplayName("Parses standard duration tokens")
        void validTokens(String token, long expectedMs) {
            assertEquals(expectedMs, MuteService.parseDurationToMillis(token));
        }

        @Test
        @DisplayName("Invalid token returns -1")
        void invalidToken_returnsNegativeOne() {
            assertEquals(-1L, MuteService.parseDurationToMillis("notatime"));
        }

        @Test
        @DisplayName("Empty string returns -1")
        void emptyString_returnsNegativeOne() {
            assertEquals(-1L, MuteService.parseDurationToMillis(""));
        }
    }

    // ─── friendlyRemaining ────────────────────────────────────────────────────

    @Nested
    @DisplayName("friendlyRemaining()")
    class FriendlyRemainingTest {

        @Test
        @DisplayName("Negative ms returns 'permanent'")
        void negative_returnsPermanent() {
            assertEquals("permanent", MuteService.friendlyRemaining(-1L));
        }

        @Test
        @DisplayName("0 ms returns '0s'")
        void zero_returnsZeroSeconds() {
            assertEquals("0s", MuteService.friendlyRemaining(0L));
        }

        @Test
        @DisplayName("30 seconds")
        void thirtySeconds() {
            assertEquals("30s", MuteService.friendlyRemaining(30_000L));
        }

        @Test
        @DisplayName("90 seconds → '1m 30s'")
        void ninetySeconds() {
            assertEquals("1m 30s", MuteService.friendlyRemaining(90_000L));
        }

        @Test
        @DisplayName("2 hours exactly → '2h'")
        void twoHours() {
            assertEquals("2h", MuteService.friendlyRemaining(2 * 3_600_000L));
        }

        @Test
        @DisplayName("1 day 2 hours 3 minutes 4 seconds")
        void fullBreakdown() {
            long ms = 86_400_000L      // 1d
                    + 2 * 3_600_000L   // 2h
                    + 3 *    60_000L   // 3m
                    + 4 *     1_000L;  // 4s
            assertEquals("1d 2h 3m 4s", MuteService.friendlyRemaining(ms));
        }
    }

    // ─── mute / isMuted / unmute ──────────────────────────────────────────────

    @Nested
    @DisplayName("Service state (mute / isMuted / unmute)")
    class ServiceStateTest {

        @Test
        @DisplayName("isMuted() returns false for unknown player")
        void unknown_notMuted() {
            assertFalse(service.isMuted(UUID.randomUUID()));
        }

        @Test
        @DisplayName("isMuted() returns true after mute() with future expiry")
        void muted_isMuted() {
            UUID uuid = UUID.randomUUID();
            long future = System.currentTimeMillis() + 60_000L;
            service.mute(uuid, future, "test", "admin");
            assertTrue(service.isMuted(uuid));
        }

        @Test
        @DisplayName("isMuted() returns true for permanent mute (until = 0)")
        void permanent_isMuted() {
            UUID uuid = UUID.randomUUID();
            service.mute(uuid, 0L, "permanent", "admin");
            assertTrue(service.isMuted(uuid));
        }

        @Test
        @DisplayName("isMuted() returns false — and auto-unmutes — when mute is expired")
        void expired_isNotMuted_andAutoRemoved() {
            UUID uuid = UUID.randomUUID();
            long past = System.currentTimeMillis() - 1_000L;
            service.mute(uuid, past, "old", "admin");

            assertFalse(service.isMuted(uuid));       // auto-removed
            assertNull(service.get(uuid));             // no longer in registry
        }

        @Test
        @DisplayName("unmute() returns true and player is no longer muted")
        void unmute_removesEntry() {
            UUID uuid = UUID.randomUUID();
            service.mute(uuid, System.currentTimeMillis() + 60_000L, "r", "a");
            assertTrue(service.unmute(uuid));
            assertFalse(service.isMuted(uuid));
        }

        @Test
        @DisplayName("unmute() returns false for unknown player")
        void unmute_unknownPlayer_returnsFalse() {
            assertFalse(service.unmute(UUID.randomUUID()));
        }

        @Test
        @DisplayName("allMuted() returns all currently muted (non-expired) players")
        void allMuted_returnsExpectedSet() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID c = UUID.randomUUID(); // expired

            long future = System.currentTimeMillis() + 60_000L;
            long past   = System.currentTimeMillis() -  1_000L;

            service.mute(a, future, "", "");
            service.mute(b, 0L, "", "");  // permanent
            service.mute(c, past, "", "");

            // trigger expiry removal by calling isMuted
            service.isMuted(c);

            var all = service.allMuted();
            assertTrue(all.contains(a));
            assertTrue(all.contains(b));
            assertFalse(all.contains(c));
        }

        @Test
        @DisplayName("get() returns MuteData for active mute")
        void get_activeMute_returnsMuteData() {
            UUID uuid   = UUID.randomUUID();
            long future = System.currentTimeMillis() + 60_000L;
            service.mute(uuid, future, "caps", "admin");

            MuteService.MuteData data = service.get(uuid);
            assertNotNull(data);
            assertEquals(uuid,    data.uuid);
            assertEquals(future,  data.until);
            assertEquals("caps",  data.reason);
            assertEquals("admin", data.by);
        }

        @Test
        @DisplayName("get() returns null and auto-removes expired mute")
        void get_expiredMute_returnsNull() {
            UUID uuid = UUID.randomUUID();
            long past = System.currentTimeMillis() - 500L;
            service.mute(uuid, past, "old", "admin");

            assertNull(service.get(uuid));
            assertFalse(service.isMuted(uuid));
        }

        @Test
        @DisplayName("Muting the same player twice overwrites the previous mute")
        void doubleMute_overwritesPrevious() {
            UUID uuid    = UUID.randomUUID();
            long first   = System.currentTimeMillis() + 10_000L;
            long second  = System.currentTimeMillis() + 60_000L;

            service.mute(uuid, first, "first", "admin");
            service.mute(uuid, second, "second", "mod");

            MuteService.MuteData data = service.get(uuid);
            assertNotNull(data);
            assertEquals(second,   data.until);
            assertEquals("second", data.reason);
            assertEquals("mod",    data.by);
        }
    }
}
