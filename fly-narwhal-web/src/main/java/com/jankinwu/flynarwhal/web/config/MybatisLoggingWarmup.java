package com.jankinwu.flynarwhal.web.config;

import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;

/**
 * MyBatis LogFactory picks its logger implementation reflectively in a static
 * initializer. Under native-image that block runs at build time, where the
 * reflective constructor lookups fail and leave the cached constructor null for
 * the whole process — every later getLog() throws with an NPE cause. Force
 * LogFactory runtime-initialized (--initialize-at-run-time in build.gradle) and
 * call this from main() before Spring touches it: useSlf4jLogging() then triggers
 * LogFactory's init at runtime, when slf4j is present and reflection works, and
 * caches Slf4jImpl's constructor so mapper scanning succeeds too. The explicit
 * class references keep those adapters reachable in the image.
 *
 * <p>The second reference is for MyBatis's own {@code Configuration} constructor, which
 * eagerly probes for the shaded {@code org.apache.ibatis.javassist.util.proxy.ProxyFactory}
 * via {@code Class.forName} and throws when the lookup fails. Holding a static reference
 * here keeps the class reachable so the probe succeeds at image runtime; nothing ever calls
 * through it, since these entities have no lazy-loaded nested queries.
 */
public final class MybatisLoggingWarmup {

    private static final Class<?>[] KEEP_REACHABLE = {
            Slf4jImpl.class,
            NoLoggingImpl.class,
            org.apache.ibatis.javassist.util.proxy.ProxyFactory.class,
    };

    private MybatisLoggingWarmup() {}

    public static void warmUp() {
        if (KEEP_REACHABLE.length == 0) {
            return;
        }
        try {
            LogFactory.useSlf4jLogging();
        } catch (Throwable t) {
            System.err.println("[narwhal] MyBatis logging warm-up failed: " + t);
        }
    }
}
