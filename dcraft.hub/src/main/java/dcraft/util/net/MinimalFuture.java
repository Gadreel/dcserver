package dcraft.util.net;


import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

public final class MinimalFuture<T> extends CompletableFuture<T> {
    private static final AtomicLong TOKENS = new AtomicLong();
    private final long id;

    public static <U> MinimalFuture<U> completedFuture(U value) {
        MinimalFuture<U> f = new MinimalFuture();
        f.complete(value);
        return f;
    }

    public static <U> CompletableFuture<U> failedFuture(Throwable ex) {
        Objects.requireNonNull(ex);
        MinimalFuture<U> f = new MinimalFuture();
        f.completeExceptionally(ex);
        return f;
    }

    public static <U> CompletableFuture<U> supply(MinimalFuture.ExceptionalSupplier<U> supplier) {
        MinimalFuture cf = new MinimalFuture();

        try {
            U value = supplier.get();
            cf.complete(value);
        } catch (Throwable var3) {
            cf.completeExceptionally(var3);
        }

        return cf;
    }

    public MinimalFuture() {
        this.id = TOKENS.incrementAndGet();
    }

    public <U> MinimalFuture<U> newIncompleteFuture() {
        return new MinimalFuture();
    }

    public void obtrudeValue(T value) {
        throw new UnsupportedOperationException();
    }

    public void obtrudeException(Throwable ex) {
        throw new UnsupportedOperationException();
    }

    public String toString() {
        String var10000 = super.toString();
        return var10000 + " (id=" + this.id + ")";
    }

    public static <U> MinimalFuture<U> of(CompletionStage<U> stage) {
        MinimalFuture<U> cf = new MinimalFuture();
        stage.whenComplete((r, t) -> {
            complete(cf, r, t);
        });
        return cf;
    }

    private static <U> void complete(CompletableFuture<U> cf, U result, Throwable t) {
        if (t == null) {
            cf.complete(result);
        } else {
            cf.completeExceptionally(t);
        }
    }

    @FunctionalInterface
    public interface ExceptionalSupplier<U> {
        U get() throws Throwable;
    }
}