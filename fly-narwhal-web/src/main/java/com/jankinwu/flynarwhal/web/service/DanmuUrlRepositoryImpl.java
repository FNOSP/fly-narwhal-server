package com.jankinwu.flynarwhal.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jankinwu.flynarwhal.core.danmu.repository.DanmuUrlRepository;
import com.jankinwu.flynarwhal.web.entity.DanmuPlatformUrl;
import com.jankinwu.flynarwhal.web.mapper.DanmuPlatformUrlMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DanmuUrlRepositoryImpl implements DanmuUrlRepository {

    private final DanmuPlatformUrlMapper mapper;

    @Override
    public List<String> findUrlsByGuid(String guid) {
        List<DanmuPlatformUrl> list = mapper.selectList(new QueryWrapper<DanmuPlatformUrl>()
                .eq("guid", guid));
        if (list == null) return new ArrayList<>();
        return list.stream().map(DanmuPlatformUrl::getUrl).collect(Collectors.toList());
    }

    @Override
    public List<String> findUrlsByParentGuid(String parentGuid) {
        List<DanmuPlatformUrl> list = mapper.selectList(new QueryWrapper<DanmuPlatformUrl>()
                .eq("parent_guid", parentGuid));
        if (list == null) return new ArrayList<>();
        return list.stream().map(DanmuPlatformUrl::getUrl).collect(Collectors.toList());
    }

    @Override
    public void saveUrls(String guid, String parentGuid, List<String> urls) {
        for (String url : urls) {
            // Insert only when this episode/season pair does not already hold the
            // URL. Resolution re-runs whenever a season is refreshed, so a plain
            // insert would accumulate a duplicate row per run and make every
            // later lookup return the same URL repeatedly.
            Long existing = mapper.selectCount(new QueryWrapper<DanmuPlatformUrl>()
                    .eq("guid", guid)
                    .eq("parent_guid", parentGuid)
                    .eq("url", url));
            if (existing != null && existing > 0) {
                continue;
            }
            DanmuPlatformUrl item = new DanmuPlatformUrl();
            item.setGuid(guid);
            item.setParentGuid(parentGuid);
            item.setUrl(url);
            mapper.insert(item);
        }
    }
}