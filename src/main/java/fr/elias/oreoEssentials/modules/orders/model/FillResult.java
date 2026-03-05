package fr.elias.oreoEssentials.modules.orders.model;

/**
 * Returned by atomic fill operations so callers know what happened.
 */
public class FillResult {

    public enum Outcome {
        SUCCESS,
        NOT_FOUND,
        ALREADY_CLOSED,   // order is COMPLETED or CANCELLED
        INSUFFICIENT_QTY, // remainingQty < fillQty at the moment of the atomic check
        ERROR
    }

    private final Outcome outcome;
    private final int     filledQty;
    private final double  paidToSeller;
    private final Order   updatedOrder; // post-update snapshot (null on failure)

    private FillResult(Outcome outcome, int filledQty, double paidToSeller, Order updatedOrder) {
        this.outcome      = outcome;
        this.filledQty    = filledQty;
        this.paidToSeller = paidToSeller;
        this.updatedOrder = updatedOrder;
    }

    public static FillResult success(int qty, double paid, Order order) {
        return new FillResult(Outcome.SUCCESS, qty, paid, order);
    }
    public static FillResult notFound()       { return new FillResult(Outcome.NOT_FOUND, 0, 0, null); }
    public static FillResult alreadyClosed()  { return new FillResult(Outcome.ALREADY_CLOSED, 0, 0, null); }
    public static FillResult insufficientQty(){ return new FillResult(Outcome.INSUFFICIENT_QTY, 0, 0, null); }
    public static FillResult error()          { return new FillResult(Outcome.ERROR, 0, 0, null); }

    public boolean isSuccess()        { return outcome == Outcome.SUCCESS; }
    public Outcome getOutcome()       { return outcome; }
    public int     getFilledQty()     { return filledQty; }
    public double  getPaidToSeller()  { return paidToSeller; }
    public Order   getUpdatedOrder()  { return updatedOrder; }
}
