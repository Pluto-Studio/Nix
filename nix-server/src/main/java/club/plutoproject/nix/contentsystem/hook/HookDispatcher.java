package club.plutoproject.nix.contentsystem.hook;

import org.jetbrains.annotations.ApiStatus;

import club.plutoproject.nix.contentsystem.item.CustomItem;
import java.util.function.Predicate;

@ApiStatus.Internal
public final class HookDispatcher {

    private HookDispatcher() {
    }

    public static <C, R> R dispatch(
        final CustomItem item,
        final ItemHook<C, R> hook,
        final C context,
        final DefaultCall<R> defaults,
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
