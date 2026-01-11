package com.jankinwu.flynarwhal.core.danmu.fetcher;

import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface DanmuFetcher {
    /**
     * Check if this fetcher supports the given URL
     */
    boolean supports(String url);

    /**
     * Fetch danmu data from the URL
     */
    List<DanmuModel> fetch(String url);

    default Map<String, String> getEpisodeUrl(String url) {
        return Collections.emptyMap();
    }

    default Map<String, String> getEmoji(String url) {
        return Collections.emptyMap();
    }
}
