package club.plutoproject.nix.contentsystem;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class ContentSystemDefaultCall<R> {

    private final Supplier<DefaultValue<R>> operation;
    private final Map<Object, Object> nativeResults = new IdentityHashMap<>();

    ContentSystemDefaultCall(final Supplier<DefaultValue<R>> operation) {
        this.operation = Objects.requireNonNull(operation, "operation");
    }

    R call() {
        final DefaultValue<R> value = Objects.requireNonNull(this.operation.get(), "default operation result");
        if (value.result() != null && value.nativeResult() != null) {
            this.nativeResults.put(value.result(), value.nativeResult());
        }
        return value.result();
    }

    boolean returned(final @Nullable Object result) {
        return this.nativeResults.containsKey(result);
    }

    @Nullable Object nativeResultFor(final @Nullable Object result) {
        return this.nativeResults.get(result);
    }

    record DefaultValue<R>(R result, @Nullable Object nativeResult) {
    }
}
