package com.blog.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置：MySQL 5.7 分页插件 + 公共字段自动填充。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页插件（DbType.MYSQL 生成 LIMIT 语法，兼容 5.7）。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大条数，防止恶意大分页拖垮数据库
        pagination.setMaxLimit(200L);
        // 超出该阈值时 count 走优化 SQL
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    /**
     * created_at / updated_at 自动填充。
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                fillIfNull(metaObject, "createdAt", now);
                fillIfNull(metaObject, "updatedAt", now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }

            private void fillIfNull(MetaObject metaObject, String field, LocalDateTime value) {
                Object current = metaObject.getValue(field);
                if (current == null) {
                    this.strictInsertFill(metaObject, field, LocalDateTime.class, value);
                }
            }
        };
    }
}
