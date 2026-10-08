package com.blog.module.article.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BusinessException;
import com.blog.common.ErrorCode;
import com.blog.module.article.dto.TagCountDTO;
import com.blog.module.article.dto.TagSaveRequest;
import com.blog.module.article.dto.TagVO;
import com.blog.module.article.entity.ArticleTag;
import com.blog.module.article.entity.Tag;
import com.blog.module.article.mapper.ArticleTagMapper;
import com.blog.module.article.mapper.TagMapper;
import com.blog.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 标签服务。
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;

    /**
     * 标签云（含文章数，字号按文章数加权）。
     */
    public List<TagVO> listWithCount() {
        List<TagCountDTO> list = tagMapper.selectWithCount();
        List<TagVO> result = new ArrayList<>(list.size());
        for (TagCountDTO dto : list) {
            Tag tag = new Tag();
            tag.setId(dto.getId());
            tag.setName(dto.getName());
            tag.setSlug(dto.getSlug());
            tag.setColor(dto.getColor());
            result.add(TagVO.from(tag, dto.getArticleCount() == null ? 0 : dto.getArticleCount().intValue()));
        }
        return result;
    }

    public List<Tag> list() {
        return tagMapper.selectList(new LambdaQueryWrapper<Tag>().orderByAsc(Tag::getId));
    }

    public Tag getById(Long id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "标签不存在");
        }
        return tag;
    }

    public Tag getBySlug(String slug) {
        Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getSlug, slug).last("LIMIT 1"));
        if (tag == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "标签不存在");
        }
        return tag;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(TagSaveRequest request) {
        String slug = request.getSlug() == null || request.getSlug().isBlank()
                ? SlugUtils.slugify(request.getName())
                : SlugUtils.slugify(request.getSlug());
        Tag exists = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getSlug, slug).last("LIMIT 1"));
        if (exists != null && (request.getId() == null || !exists.getId().equals(request.getId()))) {
            throw new BusinessException(ErrorCode.SLUG_DUPLICATE);
        }
        Tag tag = request.getId() == null ? new Tag() : getById(request.getId());
        tag.setName(request.getName().trim());
        tag.setSlug(slug);
        tag.setColor(request.getColor());
        if (request.getId() == null) {
            tagMapper.insert(tag);
        } else {
            tagMapper.updateById(tag);
        }
        return tag.getId();
    }

    /**
     * 删除标签。
     *
     * <p>已被文章引用的标签<b>不允许删除</b>：删除会连带清掉文章与标签的关联关系。
     * 如需移除被引用的标签，请使用「合并标签」把文章迁移到目标标签后再删除。
     *
     * @throws BusinessException 标签不存在，或被文章引用（{@link ErrorCode#TAG_IN_USE}）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);

        // 引用检查：存在引用该标签的文章时直接拒绝
        Long used = articleTagMapper.selectCount(new LambdaQueryWrapper<ArticleTag>()
                .eq(ArticleTag::getTagId, id));
        if (used != null && used > 0) {
            throw new BusinessException(ErrorCode.TAG_IN_USE);
        }

        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, id));
        tagMapper.deleteById(id);
    }

    /**
     * 合并标签：source 下的文章关联迁移到 target，随后删除 source。
     */
    @Transactional(rollbackFor = Exception.class)
    public void merge(Long sourceId, Long targetId) {
        if (sourceId.equals(targetId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能合并到自身");
        }
        Tag source = getById(sourceId);
        getById(targetId);
        List<ArticleTag> relations = articleTagMapper.selectList(
                new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, sourceId));
        for (ArticleTag relation : relations) {
            articleTagMapper.insertIgnore(relation.getArticleId(), targetId);
        }
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, sourceId));
        tagMapper.deleteById(source.getId());
    }

    /**
     * 根据名称批量获取（不存在则创建），用于文章保存时的标签名输入。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> resolveTagIds(List<String> names) {
        List<Long> ids = new ArrayList<>();
        if (names == null) {
            return ids;
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String trimmed = name.trim();
            Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                    .eq(Tag::getName, trimmed).last("LIMIT 1"));
            if (tag == null) {
                TagSaveRequest request = new TagSaveRequest();
                request.setName(trimmed);
                tag = tagMapper.selectById(save(request));
            }
            if (!ids.contains(tag.getId())) {
                ids.add(tag.getId());
            }
        }
        return ids;
    }
}
