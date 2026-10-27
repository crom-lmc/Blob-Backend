package com.blog.module.media.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.module.media.entity.Media;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 媒体 Mapper。
 */
@Mapper
public interface MediaMapper extends BaseMapper<Media> {

    /**
     * 全部目录（去重）。
     */
    @Select("SELECT DISTINCT folder FROM t_media WHERE folder IS NOT NULL AND folder <> '' ORDER BY folder ASC")
    List<String> selectFolders();

    /**
     * 按逻辑目录统计媒体数（folder_id 为 NULL 的归入未分组，不计入）。
     */
    @Select("SELECT folder_id AS folderId, COUNT(*) AS total FROM t_media WHERE folder_id IS NOT NULL GROUP BY folder_id")
    List<Map<String, Object>> countByFolderId();
}
