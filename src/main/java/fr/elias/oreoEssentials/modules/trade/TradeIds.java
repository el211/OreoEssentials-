package fr.elias.oreoEssentials.modules.trade;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class TradeIds {
    private TradeIds() {}

    public static UUID computeTradeId(UUID u1, UUID u2) {
        int cmp = u1.toString().compareTo(u2.toString());
        UUID a = (cmp <= 0) ? u1 : u2;
        UUID b = (cmp <= 0) ? u2 : u1;

        ByteBuffer buf = ByteBuffer.allocate(32);
        buf.putLong(a.getMostSignificantBits()).putLong(a.getLeastSignificantBits());
        buf.putLong(b.getMostSignificantBits()).putLong(b.getLeastSignificantBits());
        return UUID.nameUUIDFromBytes(buf.array());
    }
}
