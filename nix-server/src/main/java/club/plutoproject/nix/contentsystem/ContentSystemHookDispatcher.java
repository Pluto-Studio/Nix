package club.plutoproject.nix.contentsystem;

import java.util.function.Predicate;

final class ContentSystemHookDispatcher {

    private ContentSystemHookDispatcher() {
    }

    static <C, R> R dispatch(
        final ContentSystemItem item,
        final ItemHook<C, R> hook,
        final C context,
        final ContentSystemDefaultCall<R> defaults,
        final boolean nullableResult,
        final Predicate<? super R> validResult
    ) {
        final ItemHookHandler<C, R> handler = item.handler(hook);
        if (handler == null) {
            return defaults.call();
        }

        try {
            final R result = handler.handle(context);
            if ((!nullableResult && result == null) || !validResult.test(result)) {
                throw new InvalidHookResultException();
            }
            return result;
        } catch (final Throwable failure) {
            if (defaults.isDefaultFailure(failure)) {
                throwUnchecked(failure);
            }
            if (isFatal(failure)) {
                throw (Error) failure;
            }
            ContentSystemDiagnostics.hookFailure(item, hook, failure instanceof InvalidHookResultException ? "invalid-result" : "callback", failure);
            return fallback(defaults);
        }
    }

    private static void throwUnchecked(final Throwable failure) {
        if (failure instanceof Error error) {
            throw error;
        }
        throw (RuntimeException) failure;
    }

    private static <R> R fallback(final ContentSystemDefaultCall<R> defaults) {
        return defaults.completed() ? defaults.lastResult() : defaults.call();
    }

    private static boolean isFatal(final Throwable failure) {
        return failure instanceof VirtualMachineError || failure instanceof ThreadDeath || failure instanceof LinkageError;
    }

    private static final class InvalidHookResultException extends RuntimeException {
    }
}
