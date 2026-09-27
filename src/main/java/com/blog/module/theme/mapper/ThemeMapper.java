package com.blog.module.theme.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.module.theme.entity.Theme;
import org.apache.ibatis.annotations.Mapper;

/**
 * 主题 Mapper。
 */
@Mapper
public interface ThemeMapper extends BaseMapper<Theme> {
}
