package com.blog.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 统一分页结果。
 */
@Data
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> records = new ArrayList<>();

    private long total;

    private long page;

    private long size;

    @JsonIgnore
    private long pages;

    public PageResult(List<T> records, long total, long page, long size) {
        this.records = records == null ? Collections.emptyList() : records;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = size <= 0 ? 0 : (total + size - 1) / size;
    }

    public long getPages() {
        return size <= 0 ? 0 : (total + size - 1) / size;
    }

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size);
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 分页数据转换（保持分页信息不变）。
     */
    public <V> PageResult<V> convert(Function<? super T, ? extends V> mapper) {
        List<V> list = new ArrayList<>(this.records.size());
        for (T record : this.records) {
            list.add(mapper.apply(record));
        }
        return new PageResult<>(list, this.total, this.page, this.size);
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(Collections.emptyList(), 0, page, size);
    }
}
