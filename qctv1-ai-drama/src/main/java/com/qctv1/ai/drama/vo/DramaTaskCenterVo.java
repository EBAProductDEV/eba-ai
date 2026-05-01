package com.qctv1.ai.drama.vo;

import java.util.List;

public record DramaTaskCenterVo(
        Long activeCount,
        Integer totalCount,
        List<DramaTaskCenterItemVo> tasks
) {
}
