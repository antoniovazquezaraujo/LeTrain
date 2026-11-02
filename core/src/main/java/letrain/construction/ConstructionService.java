package letrain.construction;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import letrain.economy.EconomyManager;
import letrain.planner.RoutePlan;

/**
 * Shared execution state for planned construction works. It owns the active queue, the prepaid
 * quote and the lifecycle transitions, but no view or terminal. Both clients can drive the same
 * service and only translate the returned outcomes into their own UI.
 */
public final class ConstructionService {

    /** Outcome of an execution action. */
    public enum Status {
        IDLE, STARTED, RESUMED, PAUSED, CANCELLED, DONE, ACTIVE, NO_CASH, NO_QUOTE, WRONG_STATE, PREFLIGHT, REJECTED
    }

    /** Result with a ready-to-show detail and the cash actually spent on cancellation. */
    public record Outcome(Status status, String detail, double spent) {
        public Outcome {
            status = Objects.requireNonNull(status, "status must not be null");
            detail = detail == null ? "" : detail;
            if (spent < 0 || !Double.isFinite(spent)) {
                throw new IllegalArgumentException("spent must be finite and non-negative");
            }
        }

        public boolean started() {
            return status == Status.STARTED || status == Status.RESUMED;
        }
    }

    private final ConstructionPlanList works;
    private final EconomyManager economy;
    private RouteConstructionQueue queue;
    private ActiveWork activeWork;

    private record ActiveWork(int workId, WorkQuote quote, float prepaid) {
    }

    public ConstructionService(ConstructionPlanList works, EconomyManager economy) {
        this.works = Objects.requireNonNull(works, "works must not be null");
        this.economy = Objects.requireNonNull(economy, "economy must not be null");
    }

    /** Starts a manual construction (Shift+C). It never has a planned-work row or prepaid quote. */
    public boolean startManual(RoutePlan plan, TrackMaterializer materializer) {
        if (isActive()) {
            return false;
        }
        queue = new RouteConstructionQueue(economy);
        activeWork = null;
        return queue.start(plan, materializer);
    }

    /** Starts an automatic work, charging its quote once. */
    public Outcome start(PlannedWork work, WorkQuote quote, TrackMaterializer materializer) {
        if (work == null) {
            return new Outcome(Status.WRONG_STATE, "missing work", 0);
        }
        if (work.state() != PlannedWork.State.DRAFT) {
            return new Outcome(Status.WRONG_STATE, "work is already " + work.state(), 0);
        }
        if (quote == null || !quote.feasible()) {
            return new Outcome(Status.NO_QUOTE, "select a company quote first", 0);
        }
        if (isActive()) {
            return new Outcome(Status.ACTIVE, "another construction is already active", 0);
        }
        Optional<String> rejection;
        try {
            rejection = materializer.prepare(quote.plan());
        } catch (RuntimeException exception) {
            return new Outcome(Status.PREFLIGHT, describe(exception), 0);
        }
        if (rejection == null || rejection.isPresent()) {
            return new Outcome(Status.PREFLIGHT,
                    rejection == null ? "preflight returned no result" : rejection.get(), 0);
        }
        float amount = (float) quote.totalCost();
        if (economy.getBalance() < amount) {
            return new Outcome(Status.NO_CASH, Float.toString(amount), 0);
        }
        float prepaid = economy.isFreeConstruction() ? 0 : amount;
        economy.charge(amount);
        RouteConstructionQueue startedQueue = new RouteConstructionQueue(economy);
        boolean started =
                startedQueue.start(quote.plan(), materializer, quote.company().timeFactor());
        if (!started) {
            economy.refund(amount);
            return new Outcome(Status.ACTIVE, "another construction is already active", 0);
        }
        ConstructionProgress progress = startedQueue.progress();
        if (progress.state() == ConstructionState.REJECTED) {
            economy.refund(amount);
            return new Outcome(Status.REJECTED,
                    progress.reasonOptional().orElse("route cannot be built"), 0);
        }
        if (progress.state() == ConstructionState.DONE) {
            economy.refund(amount);
            return new Outcome(Status.DONE, "construction complete", 0);
        }
        works.replaceState(work.id(), PlannedWork.State.WORKING);
        queue = startedQueue;
        activeWork = new ActiveWork(work.id(), quote, prepaid);
        return new Outcome(Status.STARTED, "", 0);
    }

    /** Resumes a paused work from its saved progress without charging the quote again. */
    public Outcome resume(PlannedWork work, TrackMaterializer materializer) {
        if (work == null || work.state() != PlannedWork.State.PAUSED) {
            return new Outcome(Status.WRONG_STATE, "work is not paused", 0);
        }
        WorkQuote quote = work.quote().orElse(null);
        if (quote == null || !quote.feasible()) {
            return new Outcome(Status.NO_QUOTE, "select a company quote first", 0);
        }
        if (isActive()) {
            return new Outcome(Status.ACTIVE, "another construction is already active", 0);
        }
        Optional<String> rejection;
        try {
            rejection = materializer.prepare(quote.plan(), work.builtSteps());
        } catch (RuntimeException exception) {
            return new Outcome(Status.PREFLIGHT, describe(exception), 0);
        }
        if (rejection == null || rejection.isPresent()) {
            return new Outcome(Status.PREFLIGHT,
                    rejection == null ? "preflight returned no result" : rejection.get(), 0);
        }
        RouteConstructionQueue resumedQueue = new RouteConstructionQueue(economy);
        boolean resumed = resumedQueue.resume(quote.plan(), materializer,
                quote.company().timeFactor(), work.builtSteps(), work.remainingTicks());
        if (!resumed) {
            return new Outcome(Status.ACTIVE, "another construction is already active", 0);
        }
        ConstructionProgress progress = resumedQueue.progress();
        if (progress.state() == ConstructionState.REJECTED) {
            return new Outcome(Status.REJECTED,
                    progress.reasonOptional().orElse("route cannot be built"), 0);
        }
        if (progress.state() == ConstructionState.DONE) {
            works.replaceState(work.id(), PlannedWork.State.DONE);
            return new Outcome(Status.DONE, "construction complete", 0);
        }
        works.replaceState(work.id(), PlannedWork.State.WORKING);
        queue = resumedQueue;
        activeWork = new ActiveWork(work.id(), quote, (float) work.prepaidCost());
        return new Outcome(Status.RESUMED, "", 0);
    }

    /** Pauses the active automatic work, keeping its exact progress and prepaid cash. */
    public Outcome pause() {
        if (!isAutomatic() || queue == null) {
            return new Outcome(Status.WRONG_STATE, "no automatic work is active", 0);
        }
        ConstructionProgress progress = queue.pause();
        works.markPaused(activeWork.workId(), progress.builtSteps(), progress.remainingTicks(),
                activeWork.prepaid());
        queue = null;
        activeWork = null;
        return new Outcome(Status.PAUSED, "", 0);
    }

    /** Cancels the active work (manual or automatic) and refunds an automatic quote. */
    public Outcome cancelActive() {
        if (queue == null) {
            return new Outcome(Status.IDLE, "", 0);
        }
        ConstructionProgress progress = queue.cancel();
        double spent = 0;
        if (activeWork != null) {
            spent = refund(progress);
            works.markCancelled(activeWork.workId(), spent);
            activeWork = null;
        }
        return new Outcome(Status.CANCELLED, "", spent);
    }

    /** Cancels a paused work, refunding the unbuilt part minus the cancellation penalty. */
    public Outcome cancelPaused(PlannedWork work) {
        if (work == null || work.state() != PlannedWork.State.PAUSED) {
            return new Outcome(Status.WRONG_STATE, "work is not paused", 0);
        }
        WorkQuote quote = work.quote().orElse(null);
        if (quote == null) {
            return new Outcome(Status.NO_QUOTE, "paused work has no company quote", 0);
        }
        double spent = refundFor(quote, work.builtSteps(), work.prepaidCost());
        works.markCancelled(work.id(), spent);
        return new Outcome(Status.CANCELLED, "", spent);
    }

    /** Advances the active queue once and reports its terminal transition, if any. */
    public Outcome tick() {
        if (queue == null || !queue.isActive()) {
            return new Outcome(Status.IDLE, "", 0);
        }
        ConstructionProgress progress = queue.tick();
        if (progress.state() == ConstructionState.DONE) {
            if (activeWork != null) {
                works.replaceState(activeWork.workId(), PlannedWork.State.DONE);
                activeWork = null;
            }
            return new Outcome(Status.DONE, "", 0);
        }
        if (progress.state() == ConstructionState.CANCELLED) {
            double spent = 0;
            if (activeWork != null) {
                spent = refund(progress);
                works.markCancelled(activeWork.workId(), spent);
                activeWork = null;
            }
            return new Outcome(Status.CANCELLED, "", spent);
        }
        return new Outcome(Status.STARTED, "", 0);
    }

    public boolean isActive() {
        return queue != null && queue.isEditingLocked();
    }

    public boolean isAutomatic() {
        return activeWork != null;
    }

    public OptionalInt activeWorkId() {
        return activeWork == null ? OptionalInt.empty() : OptionalInt.of(activeWork.workId());
    }

    public Optional<WorkQuote> activeQuote() {
        return activeWork == null ? Optional.empty() : Optional.of(activeWork.quote());
    }

    public float activePrepaid() {
        return activeWork == null ? 0 : activeWork.prepaid();
    }

    public ConstructionProgress progress() {
        return queue == null ? ConstructionProgress.idle() : queue.progress();
    }

    public Optional<RouteConstructionQueue> queue() {
        return Optional.ofNullable(queue);
    }

    private double refund(ConstructionProgress progress) {
        return activeWork == null ? 0
                : refundFor(activeWork.quote(), progress.builtSteps(), activeWork.prepaid());
    }

    private double refundFor(WorkQuote quote, int builtSteps, double prepaid) {
        double unbuilt = 0;
        for (int index = builtSteps; index < quote.plan().steps().size(); index++) {
            unbuilt += quote.costForStep(index);
        }
        double penalty = Math.min(unbuilt, Math.max(unbuilt * 0.10, quote.totalCost() * 0.05));
        double refund = Math.max(0, unbuilt - penalty);
        economy.refund((float) refund);
        return Math.max(0, prepaid - refund);
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + message;
    }
}
