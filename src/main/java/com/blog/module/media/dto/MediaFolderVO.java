package com.blog.module.media.dto;

import com.blog.module.media.entity.MediaFolder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 媒体目录树节点（含目录内媒体数）。
 */
@Data
public class MediaFolderVO {

    private Long id;

    private String name;

    private Long parentId;

    private Integer sort;

    /** 目录内媒体文件数（不含子目录） */
    private Integer mediaCount;

    private List<MediaFolderVO> children = new ArrayList<>();

    public static MediaFolderVO from(MediaFolder folder) {
        MediaFolderVO vo = new MediaFolderVO();
        vo.setId(folder.getId());
        vo.setName(folder.getName());
        vo.setParentId(folder.getParentId());
        vo.setSort(folder.getSort());
        vo.setMediaCount(0);
        return vo;
    }
}
