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

        final R result = handler.handle(context);
        if ((!nullableResult && result == null) || !validResult.test(result)) {
            throw new InvalidHookResultException();
        }
        return result;
    }

    private static final class InvalidHookResultException extends RuntimeException {
    }
}
