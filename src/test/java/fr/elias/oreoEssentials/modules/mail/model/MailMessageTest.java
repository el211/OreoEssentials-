package fr.elias.oreoEssentials.modules.mail.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MailMessage}.
 *
 * <p>Pure Java — no Bukkit dependency required.</p>
 */
@DisplayName("MailMessage")
class MailMessageTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static MailMessage msg(String message, String itemData, long expiresAt) {
        return new MailMessage(
                "test-id",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Sender",
                message,
                itemData,
                System.currentTimeMillis(),
                false,
                false,
                expiresAt
        );
    }

    // ─── hasMessage() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("hasMessage()")
    class HasMessage {

        @Test
        @DisplayName("Returns true when message is non-blank")
        void nonBlank_returnsTrue() {
            assertTrue(msg("Hello!", null, 0).hasMessage());
        }

        @Test
        @DisplayName("Returns false when message is null")
        void null_returnsFalse() {
            assertFalse(msg(null, "itemdata", 0).hasMessage());
        }

        @Test
        @DisplayName("Returns false when message is blank")
        void blank_returnsFalse() {
            assertFalse(msg("   ", null, 0).hasMessage());
        }

        @Test
        @DisplayName("Returns false when message is empty string")
        void empty_returnsFalse() {
            assertFalse(msg("", null, 0).hasMessage());
        }
    }

    // ─── hasItem() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("hasItem()")
    class HasItem {

        @Test
        @DisplayName("Returns true when itemData is non-blank")
        void nonBlank_returnsTrue() {
            assertTrue(msg(null, "base64data==", 0).hasItem());
        }

        @Test
        @DisplayName("Returns false when itemData is null")
        void null_returnsFalse() {
            assertFalse(msg("hi", null, 0).hasItem());
        }

        @Test
        @DisplayName("Returns false when itemData is blank")
        void blank_returnsFalse() {
            assertFalse(msg("hi", "  ", 0).hasItem());
        }

        @Test
        @DisplayName("Returns false when itemData is empty string")
        void empty_returnsFalse() {
            assertFalse(msg("hi", "", 0).hasItem());
        }
    }

    // ─── isExpired() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isExpired()")
    class IsExpired {

        @Test
        @DisplayName("Returns false when expiresAt is 0 (never expires)")
        void zero_neverExpires() {
            assertFalse(msg("hi", null, 0).isExpired());
        }

        @Test
        @DisplayName("Returns false when expiry is in the future")
        void futureExpiry_notExpired() {
            long future = System.currentTimeMillis() + 60_000L;
            assertFalse(msg("hi", null, future).isExpired());
        }

        @Test
        @DisplayName("Returns true when expiry is in the past")
        void pastExpiry_isExpired() {
            long past = System.currentTimeMillis() - 1_000L;
            assertTrue(msg("hi", null, past).isExpired());
        }
    }

    // ─── Mutable state ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Mutable fields")
    class MutableFields {

        @Test
        @DisplayName("setRead(true) changes isRead() from false to true")
        void setRead_true() {
            MailMessage m = msg("hi", null, 0);
            assertFalse(m.isRead());
            m.setRead(true);
            assertTrue(m.isRead());
        }

        @Test
        @DisplayName("setRead(false) resets isRead() back to false")
        void setRead_false() {
            MailMessage m = msg("hi", null, 0);
            m.setRead(true);
            m.setRead(false);
            assertFalse(m.isRead());
        }

        @Test
        @DisplayName("setItemClaimed(true) changes isItemClaimed() from false to true")
        void setItemClaimed_true() {
            MailMessage m = msg(null, "data", 0);
            assertFalse(m.isItemClaimed());
            m.setItemClaimed(true);
            assertTrue(m.isItemClaimed());
        }
    }

    // ─── Accessor round-trip ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Accessors")
    class Accessors {

        @Test
        @DisplayName("All constructor values are retrievable via getters")
        void constructorValuesRetained() {
            String    id        = "mail-42";
            UUID      recipient = UUID.randomUUID();
            UUID      sender    = UUID.randomUUID();
            String    senderName = "Alice";
            String    message   = "Hello there";
            String    itemData  = "base64...";
            long      sentAt    = 1_000_000L;
            long      expiresAt = 2_000_000L;

            MailMessage m = new MailMessage(
                    id, recipient, sender, senderName,
                    message, itemData, sentAt, true, true, expiresAt
            );

            assertEquals(id,         m.getId());
            assertEquals(recipient,  m.getRecipientUuid());
            assertEquals(sender,     m.getSenderUuid());
            assertEquals(senderName, m.getSenderName());
            assertEquals(message,    m.getMessage());
            assertEquals(itemData,   m.getItemData());
            assertEquals(sentAt,     m.getSentAt());
            assertTrue(m.isRead());
            assertTrue(m.isItemClaimed());
            assertEquals(expiresAt,  m.getExpiresAt());
        }

        @Test
        @DisplayName("Broadcast mail has null recipientUuid")
        void broadcast_nullRecipient() {
            MailMessage broadcast = new MailMessage(
                    "bcast-1", null, null,
                    "Server", "Broadcast message", null,
                    System.currentTimeMillis(), false, false, 0
            );
            assertNull(broadcast.getRecipientUuid());
            assertNull(broadcast.getSenderUuid());
        }

        @Test
        @DisplayName("Console mail has null senderUuid")
        void consoleMail_nullSenderUuid() {
            MailMessage m = new MailMessage(
                    "console-mail", UUID.randomUUID(), null,
                    "Server", "Server message", null,
                    System.currentTimeMillis(), false, false, 0
            );
            assertNull(m.getSenderUuid());
            assertTrue(m.hasMessage());
        }
    }
}
