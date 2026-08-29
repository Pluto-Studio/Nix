package club.plutoproject.nix.contentsystem.hook;

import org.jetbrains.annotations.ApiStatus;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

@ApiStatus.Internal
public final class DefaultCall<R> {

    private final Supplier<DefaultValue<R>> operation;
    private final Map<Object, Object> nativeResults = new IdentityHashMap<>();

    public DefaultCall(final Supplier<DefaultValue<R>> operation) {
        this.operation = Objects.requireNonNull(operation, "operation");
    }

    public R call() {
        final DefaultValue<R> value = Objects.requireNonNull(this.operation.get(), "default operation result");
        if (value.result() != null && value.nativeResult() != null) {
            this.nativeResults.put(value.result(), value.nativeResult());
        }
        return value.result();
    }

    public boolean returned(final @Nullable Object result) {
        return this.nativeResults.containsKey(result);
    }

    public @Nullable Object nativeResultFor(final @Nullable Object result) {
        return this.nativeResults.get(result);
    }

    public record DefaultValue<R>(R result, @Nullable Object nativeResult) {
    }
}
