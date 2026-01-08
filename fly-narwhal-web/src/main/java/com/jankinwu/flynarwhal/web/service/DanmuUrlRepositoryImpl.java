package com.jankinwu.flynarwhal.web.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jankinwu.flynarwhal.core.danmu.repository.DanmuUrlRepository;
import com.jankinwu.flynarwhal.web.entity.VideoConfigUrl;
import com.jankinwu.flynarwhal.web.mapper.VideoConfigUrlMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DanmuUrlRepositoryImpl implements DanmuUrlRepository {

    private final VideoConfigUrlMapper mapper;

    @Override
    public List<String> findUrlsByGuid(String guid) {
        List<VideoConfigUrl> list = mapper.selectList(new LambdaQueryWrapper<VideoConfigUrl>()
                .eq(VideoConfigUrl::getGuid, guid));
        if (list == null) return new ArrayList<>();
        return list.stream().map(VideoConfigUrl::getUrl).collect(Collectors.toList());
    }

    @Override
    public List<String> findUrlsByParentGuid(String parentGuid) {
        List<VideoConfigUrl> list = mapper.selectList(new LambdaQueryWrapper<VideoConfigUrl>()
                .eq(VideoConfigUrl::getParentGuid, parentGuid));
        if (list == null) return new ArrayList<>();
        return list.stream().map(VideoConfigUrl::getUrl).collect(Collectors.toList());
    }

    @Override
    public void saveUrls(String guid, String parentGuid, List<String> urls) {
        for (String url : urls) {
            VideoConfigUrl item = new VideoConfigUrl();
            item.setGuid(guid);
            item.setParentGuid(parentGuid);
            item.setUrl(url);
            mapper.insert(item);
        }
    }
}

