package fr.elias.oreoEssentials.holograms;

public final class OreoHologramFactory {
    public static OreoHologram create(OreoHologramType type, String name, OreoHologramLocation loc) {
        OreoHologramData d = new OreoHologramData();
        d.name = name;
        d.type = type;
        d.location = loc;
        return fromData(d);
    }

    public static OreoHologram fromData(OreoHologramData d) {
        return switch (d.type) {
            case TEXT -> new TextOreoHologram(d.name, d);
            case ITEM -> new ItemOreoHologram(d.name, d);
            case BLOCK -> new BlockOreoHologram(d.name, d);
        };
    }
}