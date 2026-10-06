package com.jankinwu.flynarwhal.core.danmu.fetcher.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.danmu.fetcher.AbstractDanmuFetcher;
import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;
import com.jankinwu.flynarwhal.core.danmu.repository.DandanAccount;
import com.jankinwu.flynarwhal.core.danmu.repository.DanmuSourceConfigProvider;
import com.jankinwu.flynarwhal.core.danmu.service.DandanPlayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Dandanplay channel backed by the OFFICIAL open danmaku network
 * (api.dandanplay.net, signed with the appId/appSecret the client stores in
 * DANMU_SOURCE_CONFIG) instead of a third-party relay. Produces
 * {@code ddpoff:{id}} pseudo-URLs; the search layer picks this channel over
 * the relay whenever complete credentials are configured.
 *
 * <p>The client is built per call from the current DB credentials so edits
 * made in the client settings apply immediately.
 */
@Slf4j
@Component
public class DandanPlayOfficialDanmuFetcher extends AbstractDanmuFetcher {

    public static final String SCHEME = "ddpoff:";

    private final ObjectMapper objectMapper;
    @Nullable
    private final DanmuSourceConfigProvider sourceConfigProvider;

    public DandanPlayOfficialDanmuFetcher(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            ExecutorService danmuFetchExecutor,
            @Nullable DanmuSourceConfigProvider sourceConfigProvider
    ) {
        super(restTemplate, danmuFetchExecutor);
        this.objectMapper = objectMapper;
        this.sourceConfigProvider = sourceConfigProvider;
    }

    private DandanPlayClient client() {
        if (sourceConfigProvider == null) return null;
        DandanAccount account = sourceConfigProvider.getDandanAccount();
        if (account == null || !account.isComplete()) return null;
        return new DandanPlayClient(restTemplate, objectMapper, account.appId(), account.appSecret());
    }

    @Override
    public boolean supports(String url) {
        return url != null && url.startsWith(SCHEME) && client() != null;
    }

    @Override
    public Map<String, String> getEpisodeUrl(String url) {
        Map<String, String> map = new HashMap<>();
        DandanPlayClient client = client();
        if (client == null) return map;
        String animeId = url.substring(SCHEME.length());
        for (JsonNode ep : client.getEpisodes(animeId)) {
            String title = ep.path("episodeTitle").asText("");
            String episodeId = ep.path("episodeId").asText("");
            if (!title.isEmpty() && !episodeId.isEmpty()) {
                map.putIfAbsent(title, SCHEME + episodeId);
            }
        }
        return map;
    }

    @Override
    protected List<String> getLinks(String url) {
        return List.of(url);
    }

    @Override
    protected List<DanmuModel> parse(String link) {
        DandanPlayClient client = client();
        if (client == null || !link.startsWith(SCHEME)) {
            return List.of();
        }
        return client.getComments(link.substring(SCHEME.length()));
    }
}
