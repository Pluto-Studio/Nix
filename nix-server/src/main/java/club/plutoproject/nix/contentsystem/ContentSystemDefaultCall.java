package club.plutoproject.nix.contentsystem;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class ContentSystemDefaultCall<R> {

    private final Supplier<DefaultValue<R>> operation;
    private boolean completed;
    private @Nullable R lastResult;
    private final Map<Object, Object> nativeResults = new IdentityHashMap<>();
    private @Nullable Throwable defaultFailure;

    ContentSystemDefaultCall(final Supplier<DefaultValue<R>> operation) {
        this.operation = Objects.requireNonNull(operation, "operation");
    }

    R call() {
        final DefaultValue<R> value;
        try {
            value = Objects.requireNonNull(this.operation.get(), "default operation result");
        } catch (final Throwable failure) {
            this.defaultFailure = failure;
            if (failure instanceof Error error) {
                throw error;
            }
            throw (RuntimeException) failure;
        }
        this.completed = true;
        this.lastResult = value.result();
        if (value.result() != null && value.nativeResult() != null) {
            this.nativeResults.put(value.result(), value.nativeResult());
        }
        return value.result();
    }

    boolean completed() {
        return this.completed;
    }

    @Nullable R lastResult() {
        return this.lastResult;
    }

    boolean returned(final @Nullable Object result) {
        return this.completed && this.nativeResults.containsKey(result);
    }

    @Nullable Object nativeResultFor(final @Nullable Object result) {
        return this.nativeResults.get(result);
    }

    boolean isDefaultFailure(final Throwable failure) {
        return this.defaultFailure == failure;
    }

    record DefaultValue<R>(R result, @Nullable Object nativeResult) {
    }
}
