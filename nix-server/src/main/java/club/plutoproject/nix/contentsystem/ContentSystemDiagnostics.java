package club.plutoproject.nix.contentsystem;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

final class ContentSystemDiagnostics {

    private static final long REPORT_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10L);
    private static final ConcurrentHashMap<String, Long> NEXT_REPORT = new ConcurrentHashMap<>();
    private static final Logger LOGGER = Logger.getLogger("Minecraft");

    private ContentSystemDiagnostics() {
    }

    static void hookFailure(final ContentSystemItem item, final Object hook, final String category, final Throwable failure) {
        final String hookName = hook == null ? "unknown" : hook.toString();
        report("hook:" + item.key() + ':' + hookName + ':' + category, "Content System item hook failed for " + item.key(), failure);
    }

    static void projectionFailure(final ContentSystemItem item, final String component, final String binding, final Throwable failure) {
        projectionFailure(item.key(), component, binding, failure);
    }

    static void projectionFailure(final String component, final String binding, final Throwable failure) {
        projectionFailure("vanilla", component, binding, failure);
    }

    static void projectionFailure(final String itemKey, final String component, final String binding, final Throwable failure) {
        projectionFailureInternal(itemKey, component, binding, failure);
    }

    private static void projectionFailureInternal(final String itemKey, final String component, final String binding, final Throwable failure) {
        report(
            "projection:" + itemKey + ':' + component + ':' + binding,
            "Content System projection modifier failed for " + itemKey,
            failure
        );
    }

    static void recoveryFailure(final @Nullable String key, final String category, final Throwable failure) {
        final String diagnosticKey = key == null ? "unknown" : key;
        report("recovery:" + diagnosticKey + ':' + category, "Content System recovery failed for " + diagnosticKey, failure);
    }

    static void persistenceFailure(final @Nullable String key, final Throwable failure) {
        final String diagnosticKey = key == null ? "unknown" : key;
        report("persistence:" + diagnosticKey, "Content System persistence failed for " + diagnosticKey, failure);
    }

    private static void report(final String key, final String message, final Throwable failure) {
        final long now = System.nanoTime();
        final Long previous = NEXT_REPORT.putIfAbsent(key, now + REPORT_INTERVAL_NANOS);
        if (previous != null) {
            if (previous > now) {
                return;
            }
            NEXT_REPORT.replace(key, previous, now + REPORT_INTERVAL_NANOS);
        }
        LOGGER.log(Level.WARNING, message, failure);
    }

    static String componentKey(final DataComponentType<?> type) {
        final Identifier key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        return key == null ? type.toString() : key.toString();
    }
}
