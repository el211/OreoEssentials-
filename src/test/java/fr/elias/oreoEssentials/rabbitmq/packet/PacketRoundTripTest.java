package fr.elias.oreoEssentials.rabbitmq.packet;

import fr.elias.oreoEssentials.modules.tp.rabbit.packets.TpaAcceptPacket;
import fr.elias.oreoEssentials.modules.tp.rabbit.packets.TpaRequestPacket;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.SendRemoteMessagePacket;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that each {@link Packet} subclass correctly round-trips its fields
 * through {@link FriendlyByteOutputStream} → {@link FriendlyByteInputStream}.
 *
 * <p>Pure Java — no Bukkit dependency required.</p>
 */
@DisplayName("Packet round-trip serialization")
class PacketRoundTripTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Serialises a packet to a byte array via {@code writeData}. */
    private static byte[] serialize(Packet pkt) {
        FriendlyByteOutputStream out = new FriendlyByteOutputStream();
        pkt.writeData(out);
        return out.toByteArray();
    }

    /** Deserialises bytes into an existing (empty) packet via {@code readData}. */
    private static void deserialize(Packet pkt, byte[] bytes) {
        FriendlyByteInputStream in = new FriendlyByteInputStream(bytes);
        pkt.readData(in);
    }

    // ─── SendRemoteMessagePacket ──────────────────────────────────────────────

    @Nested
    @DisplayName("SendRemoteMessagePacket")
    class SendRemoteMessagePacketTest {

        @Test
        @DisplayName("Round-trip preserves all fields")
        void roundTrip_allFields() {
            UUID   targetId = UUID.randomUUID();
            String message  = "Hello, cross-server!";

            SendRemoteMessagePacket original = new SendRemoteMessagePacket(targetId, message);
            byte[] bytes = serialize(original);

            SendRemoteMessagePacket restored = new SendRemoteMessagePacket();
            deserialize(restored, bytes);

            assertEquals(original.getPacketId(), restored.getPacketId(), "packetId mismatch");
            assertEquals(targetId,  restored.getTargetId(),  "targetId mismatch");
            assertEquals(message,   restored.getMessage(),   "message mismatch");
        }

        @Test
        @DisplayName("Empty message string survives round-trip")
        void roundTrip_emptyMessage() {
            SendRemoteMessagePacket original = new SendRemoteMessagePacket(UUID.randomUUID(), "");
            SendRemoteMessagePacket restored = new SendRemoteMessagePacket();
            deserialize(restored, serialize(original));
            assertEquals("", restored.getMessage());
        }

        @Test
        @DisplayName("Unicode message survives round-trip")
        void roundTrip_unicodeMessage() {
            String unicode = "こんにちは 🌸 Héllo Wörld";
            SendRemoteMessagePacket original = new SendRemoteMessagePacket(UUID.randomUUID(), unicode);
            SendRemoteMessagePacket restored = new SendRemoteMessagePacket();
            deserialize(restored, serialize(original));
            assertEquals(unicode, restored.getMessage());
        }
    }

    // ─── TpaRequestPacket ────────────────────────────────────────────────────

    @Nested
    @DisplayName("TpaRequestPacket")
    class TpaRequestPacketTest {

        @Test
        @DisplayName("Round-trip preserves all fields")
        void roundTrip_allFields() {
            UUID   requesterUuid = UUID.randomUUID();
            UUID   targetUuid    = UUID.randomUUID();
            String requesterName = "Alice";
            String targetName    = "Bob";
            String fromServer    = "survival-1";
            long   expiresAt     = System.currentTimeMillis() + 30_000L;

            TpaRequestPacket original = new TpaRequestPacket(
                    requesterUuid, requesterName,
                    targetUuid, targetName,
                    fromServer, expiresAt
            );
            byte[] bytes = serialize(original);

            TpaRequestPacket restored = new TpaRequestPacket();
            deserialize(restored, bytes);

            assertEquals(original.getPacketId(),    restored.getPacketId(),    "packetId");
            assertEquals(requesterUuid,              restored.getRequesterUuid(), "requesterUuid");
            assertEquals(requesterName,              restored.getRequesterName(), "requesterName");
            assertEquals(targetUuid,                 restored.getTargetUuid(),    "targetUuid");
            assertEquals(targetName,                 restored.getTargetName(),    "targetName");
            assertEquals(fromServer,                 restored.getFromServer(),    "fromServer");
            assertEquals(expiresAt,                  restored.getExpiresAtEpochMs(), "expiresAt");
        }

        @Test
        @DisplayName("Null strings default to empty string")
        void roundTrip_nullStringsDefaultToEmpty() {
            // The write() guards nulls with empty strings
            TpaRequestPacket original = new TpaRequestPacket(
                    UUID.randomUUID(), null, UUID.randomUUID(), null, null, 0L
            );
            TpaRequestPacket restored = new TpaRequestPacket();
            deserialize(restored, serialize(original));

            assertEquals("", restored.getRequesterName());
            assertEquals("", restored.getTargetName());
            assertEquals("", restored.getFromServer());
        }
    }

    // ─── TpaAcceptPacket ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("TpaAcceptPacket")
    class TpaAcceptPacketTest {

        @Test
        @DisplayName("Round-trip preserves all fields")
        void roundTrip_allFields() {
            String requestId    = "req-abc-123";
            UUID   requester    = UUID.randomUUID();
            UUID   target       = UUID.randomUUID();

            TpaAcceptPacket original = new TpaAcceptPacket(
                    requestId, requester, "Alice", target, "Bob", "lobby"
            );
            byte[] bytes = serialize(original);

            TpaAcceptPacket restored = new TpaAcceptPacket();
            deserialize(restored, bytes);

            assertEquals(original.getPacketId(),  restored.getPacketId(),      "packetId");
            assertEquals(requestId,                restored.getRequestId(),      "requestId");
            assertEquals(requester,                restored.getRequesterUuid(),  "requesterUuid");
            assertEquals("Alice",                  restored.getRequesterName(),  "requesterName");
            assertEquals(target,                   restored.getTargetUuid(),     "targetUuid");
            assertEquals("Bob",                    restored.getTargetName(),     "targetName");
            assertEquals("lobby",                  restored.getFromServer(),     "fromServer");
        }

        @Test
        @DisplayName("Null strings default to empty string")
        void roundTrip_nullStringsDefaultToEmpty() {
            TpaAcceptPacket original = new TpaAcceptPacket(
                    null, UUID.randomUUID(), null, UUID.randomUUID(), null, null
            );
            TpaAcceptPacket restored = new TpaAcceptPacket();
            deserialize(restored, serialize(original));

            assertEquals("", restored.getRequestId());
            assertEquals("", restored.getRequesterName());
            assertEquals("", restored.getTargetName());
            assertEquals("", restored.getFromServer());
        }
    }

    // ─── Base Packet behaviour ────────────────────────────────────────────────

    @Nested
    @DisplayName("Packet base class")
    class BasePacketTest {

        @Test
        @DisplayName("writeData/readData preserves the original packetId")
        void packetId_isPreserved() {
            UUID knownId = UUID.randomUUID();
            SendRemoteMessagePacket original = new SendRemoteMessagePacket(UUID.randomUUID(), "test");
            original.setPacketId(knownId);

            SendRemoteMessagePacket restored = new SendRemoteMessagePacket();
            deserialize(restored, serialize(original));

            assertEquals(knownId, restored.getPacketId());
        }

        @Test
        @DisplayName("setPacketId(null) generates a new random UUID rather than NPE")
        void setPacketId_null_generatesNew() {
            SendRemoteMessagePacket pkt = new SendRemoteMessagePacket(UUID.randomUUID(), "msg");
            assertDoesNotThrow(() -> pkt.setPacketId(null));
            assertNotNull(pkt.getPacketId());
        }
    }
}
