package fr.elias.oreoEssentials.modules.shop.models;

public final class PriceModifier {

    private double buyModifier  = 1.0;
    private double sellModifier = 1.0;

    public PriceModifier() {}

    public PriceModifier(double buyModifier, double sellModifier) {
        this.buyModifier  = buyModifier;
        this.sellModifier = sellModifier;
    }

    public double applyBuy(double price)  { return price * buyModifier; }
    public double applySell(double price) { return price * sellModifier; }

    public double getBuyModifier()           { return buyModifier; }
    public double getSellModifier()          { return sellModifier; }
    public void   setBuyModifier(double v)   { this.buyModifier  = v; }
    public void   setSellModifier(double v)  { this.sellModifier = v; }

    public boolean isDefault() {
        return buyModifier == 1.0 && sellModifier == 1.0;
    }
}