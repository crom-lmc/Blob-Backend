package com.blog.module.media.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BusinessException;
import com.blog.common.ErrorCode;
import com.blog.module.media.dto.MediaFolderVO;
import com.blog.module.media.entity.Media;
import com.blog.module.media.entity.MediaFolder;
import com.blog.module.media.mapper.MediaFolderMapper;
import com.blog.module.media.mapper.MediaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 媒体目录服务：目录树、新增、重命名、删除（仅空目录可删）。
 */
@Service
@RequiredArgsConstructor
public class MediaFolderService {

    private final MediaFolderMapper folderMapper;
    private final MediaMapper mediaMapper;

    /**
     * 目录树（含每个目录的媒体数，支持任意层级）。
     */
    public List<MediaFolderVO> tree() {
        List<MediaFolder> all = folderMapper.selectList(new LambdaQueryWrapper<MediaFolder>()
                .orderByAsc(MediaFolder::getSort)
                .orderByAsc(MediaFolder::getId));

        Map<Long, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : mediaMapper.countByFolderId()) {
            Object fid = row.get("folderId");
            if (fid == null) {
                continue;
            }
            counts.put(Long.valueOf(fid.toString()), Integer.valueOf(row.get("total").toString()));
        }

        Map<Long, MediaFolderVO> map = new HashMap<>();
        for (MediaFolder folder : all) {
            MediaFolderVO vo = MediaFolderVO.from(folder);
            vo.setMediaCount(counts.getOrDefault(folder.getId(), 0));
            map.put(vo.getId(), vo);
        }

        List<MediaFolderVO> roots = new ArrayList<>();
        for (MediaFolderVO vo : map.values()) {
            MediaFolderVO parent = vo.getParentId() == null || vo.getParentId() == 0 ? null : map.get(vo.getParentId());
            if (parent == null) {
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }
        return roots;
    }

    public Long create(String name, Long parentId) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目录名称不能为空");
        }
        long pid = parentId == null ? 0 : parentId;
        if (pid != 0 && folderMapper.selectById(pid) == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "父目录不存在");
        }
        MediaFolder folder = new MediaFolder();
        folder.setName(name.trim());
        folder.setParentId(pid);
        folder.setSort(0);
        folderMapper.insert(folder);
        return folder.getId();
    }

    public void rename(Long id, String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目录名称不能为空");
        }
        MediaFolder folder = folderMapper.selectById(id);
        if (folder == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "目录不存在");
        }
        folder.setName(name.trim());
        folderMapper.updateById(folder);
    }

    /**
     * 删除目录：有子目录或目录内有媒体文件时禁止删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MediaFolder folder = folderMapper.selectById(id);
        if (folder == null) {
            return;
        }
        Long childCount = folderMapper.selectCount(
                new LambdaQueryWrapper<MediaFolder>().eq(MediaFolder::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目录下存在子目录，请先删除子目录");
        }
        Long mediaCount = mediaMapper.selectCount(
                new LambdaQueryWrapper<Media>().eq(Media::getFolderId, id));
        if (mediaCount != null && mediaCount > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目录下存在媒体文件，请先删除或移出文件");
        }
        folderMapper.deleteById(id);
    }
}
