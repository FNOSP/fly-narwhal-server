package com.jankinwu.flynarwhal.web.mapstruct;

import com.jankinwu.flynarwhal.core.data.QueuedEpisode;
import com.jankinwu.flynarwhal.core.dto.request.EpisodeDetailRequest;
import com.jankinwu.flynarwhal.web.entity.EpisodeSegment;
import com.jankinwu.flynarwhal.web.entity.TvSeasonInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AnalysisEntityMapper {

    @Mapping(target = "seasonGuid", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateTvSeasonInfo(@MappingTarget TvSeasonInfo target, String seasonFolderPath, String tvTitle, Integer seasonNumber);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "seasonGuid", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "duration", ignore = true)
    @Mapping(target = "introStart", ignore = true)
    @Mapping(target = "introEnd", ignore = true)
    @Mapping(target = "creditsStart", ignore = true)
    @Mapping(target = "creditsEnd", ignore = true)
    @Mapping(target = "introFingerprint", ignore = true)
    @Mapping(target = "creditsFingerprint", ignore = true)
    @Mapping(target = "action", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateEpisodeFromRequest(@MappingTarget EpisodeSegment target, EpisodeDetailRequest source);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "seasonGuid", ignore = true)
    @Mapping(source = "episodeGuid", target = "guid")
    @Mapping(source = "path", target = "filePath")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "action", ignore = true)
    @Mapping(target = "introStart", ignore = true)
    @Mapping(target = "introEnd", ignore = true)
    @Mapping(target = "creditsStart", ignore = true)
    @Mapping(target = "creditsEnd", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateEpisodeFromQueuedEpisode(@MappingTarget EpisodeSegment target, QueuedEpisode source);
}
