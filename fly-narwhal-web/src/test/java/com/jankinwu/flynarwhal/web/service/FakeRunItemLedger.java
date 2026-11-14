package com.jankinwu.flynarwhal.web.service;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanRunItem;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailScanRunItemMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * In-memory stand-in for the run-item ledger that honours conditional updates.
 * A plain {@code thenReturn(1)} stub would discard every write, and the
 * {@code AND STATUS = 'QUEUED'} guard is exactly what makes settlement
 * idempotent — so the tests need a fake that really applies it.
 *
 * <p>Only the operations the worker and the config service use are implemented;
 * anything else fails loudly rather than silently returning a wrong answer.
 */
final class FakeRunItemLedger {

    /** {@code COLUMN=#{ew.paramNameValuePairs.MPGENVAL3}} in a SET or WHERE fragment. */
    private static final Pattern BOUND = Pattern.compile(
            "(\\w+)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}");

    private final Map<Long, ThumbnailScanRunItem> rows = new LinkedHashMap<>();
    private long sequence;
    final ThumbnailScanRunItemMapper mapper = mock(ThumbnailScanRunItemMapper.class);

    FakeRunItemLedger() {
        when(mapper.insert(any(ThumbnailScanRunItem.class))).thenAnswer(invocation -> {
            ThumbnailScanRunItem item = invocation.getArgument(0);
            item.setId(++sequence);
            rows.put(item.getId(), item);
            return 1;
        });
        when(mapper.selectCount(any())).thenAnswer(invocation ->
                (long) matching(invocation.getArgument(0)).size());
        when(mapper.selectOne(any())).thenAnswer(invocation -> {
            List<ThumbnailScanRunItem> found = matching(invocation.getArgument(0));
            return found.isEmpty() ? null : found.get(0);
        });
        when(mapper.selectList(any())).thenAnswer(invocation -> matching(invocation.getArgument(0)));
        when(mapper.update(any(), any())).thenAnswer(invocation -> {
            UpdateWrapper<ThumbnailScanRunItem> wrapper = invocation.getArgument(1);
            Map<String, Object> assignments = boundValues(wrapper.getSqlSet(), wrapper);
            int updated = 0;
            for (ThumbnailScanRunItem item : matching(wrapper)) {
                apply(item, assignments);
                updated++;
            }
            return updated;
        });
        when(mapper.delete(any())).thenAnswer(invocation -> {
            List<ThumbnailScanRunItem> doomed = matching(invocation.getArgument(0));
            doomed.forEach(item -> rows.remove(item.getId()));
            return doomed.size();
        });
    }

    List<ThumbnailScanRunItem> all() {
        return new ArrayList<>(rows.values());
    }

    /** The item a run recorded for one media path. */
    ThumbnailScanRunItem forPath(String path) {
        String scanGuid = ThumbnailService.syntheticGuid(path);
        return rows.values().stream()
                .filter(item -> scanGuid.equals(item.getScanGuid()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no ledger item for " + path));
    }

    private List<ThumbnailScanRunItem> matching(Wrapper<ThumbnailScanRunItem> wrapper) {
        if (!(wrapper instanceof AbstractWrapper<?, ?, ?> condition)) {
            throw new UnsupportedOperationException("unsupported wrapper: " + wrapper);
        }
        Map<String, Object> equality = boundValues(condition.getSqlSegment(), condition);
        List<ThumbnailScanRunItem> found = new ArrayList<>();
        for (ThumbnailScanRunItem item : rows.values()) {
            if (equality.entrySet().stream().allMatch(e -> e.getValue().equals(value(item, e.getKey())))) {
                found.add(item);
            }
        }
        // The only ordering the code asks for: newest run first.
        if (condition.getSqlSegment().contains("ORDER BY RUN_ID DESC")) {
            found.sort((a, b) -> Long.compare(b.getRunId(), a.getRunId()));
        }
        return found;
    }

    private static Map<String, Object> boundValues(String sql, AbstractWrapper<?, ?, ?> wrapper) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (sql == null) {
            return values;
        }
        Matcher matcher = BOUND.matcher(sql);
        while (matcher.find()) {
            values.put(matcher.group(1).toUpperCase(),
                    wrapper.getParamNameValuePairs().get(matcher.group(2)));
        }
        return values;
    }

    private static Object value(ThumbnailScanRunItem item, String column) {
        return switch (column) {
            case "ID" -> item.getId();
            case "RUN_ID" -> item.getRunId();
            case "SCAN_GUID" -> item.getScanGuid();
            case "TASK_GUID" -> item.getTaskGuid();
            case "STATUS" -> item.getStatus();
            default -> throw new UnsupportedOperationException("unsupported column: " + column);
        };
    }

    private static void apply(ThumbnailScanRunItem item, Map<String, Object> assignments) {
        assignments.forEach((column, value) -> {
            switch (column) {
                case "STATUS" -> item.setStatus((String) value);
                case "TASK_GUID" -> item.setTaskGuid((String) value);
                case "ERROR_MESSAGE" -> item.setErrorMessage((String) value);
                case "UPDATE_TIME" -> item.setUpdateTime((java.time.LocalDateTime) value);
                default -> throw new UnsupportedOperationException("unsupported assignment: " + column);
            }
        });
    }
}