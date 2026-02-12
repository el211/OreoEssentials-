package fr.elias.oreoEssentials.modules.trade.rabbit.namespace;

import fr.elias.oreoEssentials.modules.trade.rabbit.packet.*;
import fr.elias.oreoEssentials.rabbitmq.namespace.PacketNamespace;

public final class TradePacketNamespace extends PacketNamespace {

    public static final short NS_ID = (short) 27;

    public static final int TRADE_START_ID  = 27000;


    public static final int TRADE_INVITE_ID   = 27001;
    public static final int TRADE_STATE_ID    = 27002;
    public static final int TRADE_CONFIRM_ID  = 27003;
    public static final int TRADE_CANCEL_ID   = 27004;
    public static final int TRADE_GRANT_ID    = 27005;

    public TradePacketNamespace() {
        super(NS_ID);
    }

    @Override
    protected void registerPackets() {
        registerPacket(TRADE_START_ID,  TradeStartPacket.class,  TradeStartPacket::new);

        registerPacket(TRADE_INVITE_ID,  TradeInvitePacket.class,  TradeInvitePacket::new);
        registerPacket(TRADE_STATE_ID,   TradeStatePacket.class,   TradeStatePacket::new);
        registerPacket(TRADE_CONFIRM_ID, TradeConfirmPacket.class, TradeConfirmPacket::new);
        registerPacket(TRADE_CANCEL_ID,  TradeCancelPacket.class,  TradeCancelPacket::new);
        registerPacket(TRADE_GRANT_ID,   TradeGrantPacket.class,   TradeGrantPacket::new);
    }
}
