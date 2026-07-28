package fr.elias.oreoEssentials.modules.grouprtp.model;

import fr.elias.oreoEssentials.util.OreTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live state for one active portal session.
 *
 * State machine:
 * <pre>
 *   IDLE → (minPlayers reached)  → COUNTDOWN
 *   COUNTDOWN → (players leave, size < min) → IDLE  (countdown cancelled)
 *   COUNTDOWN → (reaches 0)      → TELEPORTING  (then cleaned up)
 * </pre>
 *
 * Thread safety: all volatile/ConcurrentHashMap; mutations should happen
 * on the global-region scheduler (via OreScheduler.run) to stay consistent.
 */
public final class GroupSession {

    public enum State { IDLE, COUNTDOWN, TELEPORTING }

    private final String         portalId;
    private final Set<UUID>      inside   = ConcurrentHashMap.newKeySet();
    private volatile State       state    = State.IDLE;
    private volatile int         secondsLeft;
    private volatile OreTask     countdownTask;

    public GroupSession(String portalId) {
        this.portalId = portalId;
    }

    // ── Membership ────────────────────────────────────────────────────────────

    public void add(UUID uuid)    { inside.add(uuid); }
    public void remove(UUID uuid) { inside.remove(uuid); }
    public int  size()            { return inside.size(); }
    public boolean contains(UUID uuid) { return inside.contains(uuid); }

    /** Snapshot of current players — safe to iterate after tick. */
    public Set<UUID> snapshot()   { return Set.copyOf(inside); }

    // ── Countdown control ─────────────────────────────────────────────────────

    public void startCountdown(int seconds, OreTask task) {
        this.secondsLeft  = seconds;
        this.countdownTask = task;
        this.state        = State.COUNTDOWN;
    }

    public void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        state = State.IDLE;
    }

    public boolean tickDown() {
        if (secondsLeft > 0) secondsLeft--;
        return secondsLeft <= 0;
    }

    public void beginTeleporting() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        state = State.TELEPORTING;
        inside.clear(); // prevent re-entry during teleport phase
    }

    /**
     * Resets the session back to IDLE after a teleport completes or is aborted.
     * Clears any stale task reference so the session can be reused.
     */
    public void resetToIdle() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        inside.clear();
        secondsLeft = 0;
        state = State.IDLE;
    }

    /**
     * Removes a member during the TELEPORTING phase (e.g. player disconnects
     * mid-teleport). If the session becomes empty the state is reset to IDLE so
     * the session cannot remain stuck forever.
     */
    public void removeFromTeleporting(UUID uuid) {
        inside.remove(uuid);
        if (inside.isEmpty() && state == State.TELEPORTING) {
            resetToIdle();
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getPortalId()    { return portalId; }
    public State  getState()       { return state; }
    public int    getSecondsLeft() { return secondsLeft; }
    public boolean isIdle()        { return state == State.IDLE; }
    public boolean isCountdown()   { return state == State.COUNTDOWN; }
    public boolean isTeleporting() { return state == State.TELEPORTING; }
}
