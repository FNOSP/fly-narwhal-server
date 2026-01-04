package com.jankinwu.flynarwhal.core.scanner;

import com.jankinwu.flynarwhal.core.data.QueuedEpisode;
import com.jankinwu.flynarwhal.core.dto.request.EpisodeDetailRequest;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class MediaFileScanner {

    private static final Pattern EPISODE_PATTERN = Pattern.compile("[Ss]\\d+[Ee](\\d+)");
    // Simple extension check, can be expanded
    private static final List<String> VIDEO_EXTENSIONS = List.of(".mkv", ".mp4", ".avi", ".mov", ".webm");

    public List<QueuedEpisode> getEpisodeQueue(String seriesGuid, String folderPath, List<EpisodeDetailRequest> requestEpisodes) {
        List<QueuedEpisode> episodes = new ArrayList<>();
        for (EpisodeDetailRequest requestEpisode : requestEpisodes) {
            QueuedEpisode episode = new QueuedEpisode();
            episode.setSeriesGuid(seriesGuid);
            episode.setPath(requestEpisode.getFilePath());
            episode.setEpisodeNumber(requestEpisode.getEpisodeNumber());
            episode.setEpisodeGuid(requestEpisode.getGuid());
            episodes.add(episode);
        }
//        try (Stream<Path> paths = Files.walk(Paths.get(folderPath))) {
//            List<File> files = paths
//                    .filter(Files::isRegularFile)
//                    .map(Path::toFile)
//                    .filter(this::isVideoFile)
//                    .toList();
//
//            for (File file : files) {
//                int index = parseEpisodeIndex(file.getName());
//                if (index > 0) {
//                    QueuedEpisode ep = new QueuedEpisode();
//                    ep.setSeriesGuid(seriesGuid);
//                    ep.setPath(file.getAbsolutePath());
//                    ep.setEpisodeIndex(index);
//                    ep.setEpisodeId(seriesGuid + "-E" + index); // Simple ID generation
//                    episodes.add(ep);
//                }
//            }
//        } catch (IOException e) {
//            log.error("Error scanning folder: {}", folderPath, e);
//        }
        
        episodes.sort(Comparator.comparingInt(QueuedEpisode::getEpisodeNumber));
        return episodes;
    }

    private boolean isVideoFile(File file) {
        String name = file.getName().toLowerCase();
        return VIDEO_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private int parseEpisodeIndex(String filename) {
        Matcher m = EPISODE_PATTERN.matcher(filename);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }
}
