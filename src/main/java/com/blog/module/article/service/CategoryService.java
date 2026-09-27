package com.blog.module.article.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blog.common.BusinessException;
import com.blog.common.ErrorCode;
import com.blog.module.article.dto.CategoryCountDTO;
import com.blog.module.article.dto.CategorySaveRequest;
import com.blog.module.article.dto.CategoryVO;
import com.blog.module.article.entity.Article;
import com.blog.module.article.entity.Category;
import com.blog.module.article.mapper.ArticleMapper;
import com.blog.module.article.mapper.CategoryMapper;
import com.blog.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类服务（支持两级结构，树形返回）。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    public List<Category> list() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByDesc(Category::getSort)
                .orderByAsc(Category::getId));
    }

    /**
     * 分类树（含实时文章数，用于前台分类页与后台树形表格）。
     */
    public List<CategoryVO> tree() {
        List<Category> all = list();
        Map<Long, Long> counts = new HashMap<>();
        for (CategoryCountDTO dto : articleMapper.selectPublishedCountByCategory()) {
            counts.put(dto.getCategoryId(), dto.getTotal());
        }
        Map<Long, CategoryVO> map = new HashMap<>();
        List<CategoryVO> roots = new ArrayList<>();
        for (Category category : all) {
            CategoryVO vo = CategoryVO.from(category);
            vo.setArticleCount(counts.getOrDefault(category.getId(), 0L).intValue());
            map.put(category.getId(), vo);
        }
        for (CategoryVO vo : map.values()) {
            Long parentId = vo.getParentId();
            CategoryVO parent = parentId == null || parentId == 0 ? null : map.get(parentId);
            if (parent == null) {
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }
        // 父分类汇总子分类文章数（两级结构，自底向上累加）
        for (CategoryVO root : roots) {
            fillTotal(root);
        }
        roots.sort((a, b) -> {
            int s = Integer.compare(b.getSort() == null ? 0 : b.getSort(), a.getSort() == null ? 0 : a.getSort());
            return s != 0 ? s : a.getId().compareTo(b.getId());
        });
        return roots;
    }

    /**
     * 递归汇总文章数：父分类 = 自身直接文章数 + 所有子分类文章数。
     */
    private int fillTotal(CategoryVO vo) {
        int total = vo.getArticleCount() == null ? 0 : vo.getArticleCount();
        for (CategoryVO child : vo.getChildren()) {
            total += fillTotal(child);
        }
        vo.setArticleCount(total);
        return total;
    }

    public Category getById(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "分类不存在");
        }
        return category;
    }

    public Category getBySlug(String slug) {
        Category category = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                .eq(Category::getSlug, slug).last("LIMIT 1"));
        if (category == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "分类不存在");
        }
        return category;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(CategorySaveRequest request) {
        String slug = request.getSlug() == null || request.getSlug().isBlank()
                ? SlugUtils.slugify(request.getName())
                : SlugUtils.slugify(request.getSlug());
        checkSlugUnique(slug, request.getId());

        Category category = request.getId() == null ? new Category() : getById(request.getId());
        category.setName(request.getName().trim());
        category.setSlug(slug);
        category.setDescription(request.getDescription());
        category.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        category.setSort(request.getSort() == null ? 0 : request.getSort());
        if (category.getParentId().equals(category.getId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "父分类不能是自己");
        }
        if (request.getId() == null) {
            category.setArticleCount(0);
            categoryMapper.insert(category);
        } else {
            categoryMapper.updateById(category);
        }
        return category.getId();
    }

    /**
     * 删除分类：子分类上移到父级，文章解除关联。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Category category = getById(id);
        Long parentId = category.getParentId() == null ? 0L : category.getParentId();
        Category childUpdate = new Category();
        childUpdate.setParentId(parentId);
        categoryMapper.update(childUpdate, new LambdaQueryWrapper<Category>().eq(Category::getParentId, id));

        // 解除文章关联：把 category_id 置空。
        // 注意：MyBatis-Plus 的实体更新默认忽略 null 字段，用全 null 实体 update 会生成
        // `UPDATE t_article SET WHERE ...` 这种 SET 为空的非法 SQL，故这里必须用 UpdateWrapper.set 显式写 NULL。
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .set(Article::getCategoryId, null)
                .eq(Article::getCategoryId, id));

        categoryMapper.deleteById(id);
    }

    /**
     * 按当前分类排序批量更新（后台拖拽排序）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void sort(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        int size = ids.size();
        for (int i = 0; i < size; i++) {
            Category category = new Category();
            category.setId(ids.get(i));
            category.setSort(size - i);
            categoryMapper.updateById(category);
        }
    }

    public void recount(Long id) {
        categoryMapper.recount(id);
    }

    private void checkSlugUnique(String slug, Long selfId) {
        Category exists = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                .eq(Category::getSlug, slug).last("LIMIT 1"));
        if (exists != null && (selfId == null || !exists.getId().equals(selfId))) {
            throw new BusinessException(ErrorCode.SLUG_DUPLICATE);
        }
    }
}
