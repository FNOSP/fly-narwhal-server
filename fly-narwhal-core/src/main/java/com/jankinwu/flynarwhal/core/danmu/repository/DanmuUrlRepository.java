package com.jankinwu.flynarwhal.core.danmu.repository;

import java.util.List;

public interface DanmuUrlRepository {
    List<String> findUrlsByGuid(String guid);

    List<String> findUrlsByParentGuid(String parentGuid);

    void saveUrls(String guid, String parentGuid, List<String> urls);
}

