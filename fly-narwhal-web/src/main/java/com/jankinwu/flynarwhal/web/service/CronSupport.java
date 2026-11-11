package com.jankinwu.flynarwhal.web.service;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Quartz-dialect cron support for the scheduled thumbnail scan, backed by
 * cron-utils. The QUARTZ definition accepts the 6- and 7-field expressions the
 * client's generator produces (including {@code ?}, {@code L}, {@code W} and
 * {@code #}). Only the parser and ExecutionTime are used — both are pure
 * computation, so the native image needs no extra reflection or resource
 * configuration. Human-readable descriptions are rendered client-side.
 */
public final class CronSupport {

    public static final int MAX_PREVIEW_COUNT = 20;
    public static final int DEFAULT_PREVIEW_COUNT = 8;

    private static final CronDefinition QUARTZ_DEFINITION =
            CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ);
    private static final CronParser PARSER = new CronParser(QUARTZ_DEFINITION);

    private CronSupport() {
    }

    /**
     * Parse a cron expression.
     *
     * @throws IllegalArgumentException when blank or not valid Quartz cron
     */
    public static Cron parseOrThrow(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("cron expression is required");
        }
        try {
            return PARSER.parse(expression.trim());
        } catch (RuntimeException e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new IllegalArgumentException("invalid cron expression: " + reason, e);
        }
    }

    public static boolean isValid(String expression) {
        try {
            parseOrThrow(expression);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Next fire time at or after {@code from}; empty when the expression can never fire again. */
    public static Optional<ZonedDateTime> nextExecution(String expression, ZonedDateTime from) {
        return ExecutionTime.forCron(parseOrThrow(expression)).nextExecution(from);
    }

    /** The next {@code count} fire times after {@code from}, ascending. */
    public static List<ZonedDateTime> nextExecutions(String expression, ZonedDateTime from, int count) {
        int limit = Math.max(1, Math.min(count, MAX_PREVIEW_COUNT));
        ExecutionTime executionTime = ExecutionTime.forCron(parseOrThrow(expression));
        List<ZonedDateTime> runs = new ArrayList<>(limit);
        ZonedDateTime cursor = from;
        while (runs.size() < limit) {
            Optional<ZonedDateTime> next = executionTime.nextExecution(cursor);
            if (next.isEmpty()) {
                break;
            }
            runs.add(next.get());
            cursor = next.get();
        }
        return runs;
    }
}
