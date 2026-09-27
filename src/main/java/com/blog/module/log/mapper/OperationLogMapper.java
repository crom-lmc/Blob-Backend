package com.blog.module.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.module.log.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper。
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
