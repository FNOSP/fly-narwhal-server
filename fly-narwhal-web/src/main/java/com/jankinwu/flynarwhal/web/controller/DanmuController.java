package com.jankinwu.flynarwhal.web.controller;

    import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;
import com.jankinwu.flynarwhal.core.danmu.service.DanmuAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/danmu")
@RequiredArgsConstructor
public class DanmuController {

    private final DanmuAppService danmuAppService;

    @GetMapping("/get")
    public Object getDanmu(
            @RequestParam(value = "douban_id", required = false) String doubanId,
            @RequestParam(value = "episode_number", required = false) Integer episodeNumber,
            @RequestParam(value = "episode_title", required = false) String episodeTitle,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "season_number", required = false) String seasonNumber,
            @RequestParam(value = "season", required = false, defaultValue = "false") boolean season,
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "guid", required = false) String guid,
            @RequestParam(value = "parent_guid", required = false) String parentGuid,
            @RequestParam(value = "type", required = false, defaultValue = "json") String type
    ) {
        return danmuAppService.getDanmu(doubanId, title, seasonNumber, episodeNumber, season, url, guid, parentGuid, episodeTitle, type);
    }

    @GetMapping("/getEmoji")
    public Map<String, String> getEmoji(
            @RequestParam(value = "douban_id", required = false) String doubanId,
            @RequestParam(value = "episode_number", required = false) Integer episodeNumber,
            @RequestParam(value = "episode_title", required = false) String episodeTitle,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "season_number", required = false) String seasonNumber,
            @RequestParam(value = "season", required = false, defaultValue = "false") boolean season,
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "guid", required = false) String guid,
            @RequestParam(value = "parent_guid", required = false) String parentGuid,
            @RequestParam(value = "type", required = false, defaultValue = "json") String type
    ) {
        return danmuAppService.getEmoji(doubanId, title, seasonNumber, episodeNumber, season, url, guid, parentGuid, episodeTitle, type);
    }
}
