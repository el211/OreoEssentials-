package fr.elias.oreoEssentials.modules.currency.rabbitmq;

import fr.elias.oreoEssentials.rabbitmq.namespace.PacketNamespace;

/**
 * RabbitMQ packet namespace for currency system
 */
public class CurrencyPacketNamespace extends PacketNamespace {

    public CurrencyPacketNamespace() {
        super((short) 7);
    }

    @Override
    protected void registerPackets() {
        registerPacket(1, CurrencyTransferPacket.class, CurrencyTransferPacket::new);

        registerPacket(2, CurrencyUpdatePacket.class, CurrencyUpdatePacket::new);

        registerPacket(3, CurrencySyncPacket.class, CurrencySyncPacket::new);
    }
}