package com.blog.module.comment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.module.comment.entity.Comment;
import com.blog.module.stat.dto.DailyCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论 Mapper。
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * 仪表盘：按天统计评论数。
     */
    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS date, COUNT(*) AS count "
            + "FROM t_comment WHERE created_at >= #{start} "
            + "GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d') ORDER BY date ASC")
    List<DailyCountVO> selectDailyCommentCount(@Param("start") LocalDateTime start);
}
