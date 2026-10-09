package com.jankinwu.flynarwhal.core.danmu.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Title-matching behavior for the search flow. The core module has no
 * spring-boot dependency, so the list-valued settings are comma/semicolon
 * separated strings bound with {@code @Value} (the same pattern DanmuFileCache
 * uses) instead of {@code @ConfigurationProperties}.
 *
 * <pre>
 * danmu:
 *   match:
 *     platform-order: "bilibili,qq"     # "," separated
 *     title-mapping: "唐朝诡事录->唐朝诡事录之西行;国色芳华->锦绣芳华"   # ";" separated
 *     noise-patterns: "regex1;;regex2"  # ";;" separated; empty = built-in default
 *     strict-title: false
 * </pre>
 */
@Data
@Component
public class DanmuMatchProperties {

    /** Built-in default: strip bracketed release noise like （真彩）/【4K】/(完整版). */
    public static final List<String> DEFAULT_NOISE_PATTERNS = List.of(
            "[（(【\\[](真彩|高清|超清|完整版?|4K|1080[Pp])[)）\\]】]"
    );

    /**
     * Preferred platform order for ranking candidate play URLs. Recognized keys:
     * qq/tencent, iqiyi/qiyi, bilibili/bili, youku, mgtv/mango, sohu; any other
     * key is treated as a domain fragment (future sources work without code
     * changes). Empty keeps the built-in order qq &gt; iqiyi &gt; bilibili &gt;
     * youku &gt; mgtv &gt; sohu.
     */
    private List<String> platformOrder = new ArrayList<>();

    /**
     * Exact whole-title replacements applied before searching, each formatted
     * {@code 原始标题->映射标题}. Handles renamed works and subtitle drift that
     * no fuzzy matcher can guess.
     */
    private List<String> titleMapping = new ArrayList<>();

    /**
     * Regex patterns stripped from the title before searching. Invalid patterns
     * are skipped with a warning at use time.
     */
    private List<String> noisePatterns = new ArrayList<>(DEFAULT_NOISE_PATTERNS);

    /**
     * When true, search candidates must equal or start with the query token
     * instead of merely containing it, and the "any first row" last-resort
     * fallback is disabled — searching 遮天 no longer lands on 古惑仔3之只手遮天.
     */
    private boolean strictTitle = false;

    public DanmuMatchProperties() {
    }

    @Autowired
    public DanmuMatchProperties(
            @Value("${danmu.match.platform-order:}") String platformOrder,
            @Value("${danmu.match.title-mapping:}") String titleMapping,
            @Value("${danmu.match.noise-patterns:}") String noisePatterns,
            @Value("${danmu.match.strict-title:false}") boolean strictTitle
    ) {
        this.platformOrder = split(platformOrder, ",");
        this.titleMapping = split(titleMapping, ";");
        List<String> parsedNoise = split(noisePatterns, ";;");
        if (!parsedNoise.isEmpty()) {
            this.noisePatterns = parsedNoise;
        }
        this.strictTitle = strictTitle;
    }

    private static List<String> split(String raw, String separator) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        Arrays.stream(raw.split(java.util.regex.Pattern.quote(separator)))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(out::add);
        return out;
    }
}
