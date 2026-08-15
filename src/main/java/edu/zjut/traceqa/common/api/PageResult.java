package edu.zjut.traceqa.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

/**
 * 通用分页结果结构。
 *
 * <p>统一承载 MyBatis-Plus 分页查询结果，避免各接口自行拼装分页参数。</p>
 *
 * @param <T> 分页元素类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页码（从 1 开始） */
    private long page;
    /** 每页大小 */
    private long size;
    /** 总记录数 */
    private long total;
    /** 当前页数据 */
    private List<T> records;

    /** 从 MyBatis-Plus 分页对象构造 */
    public static <T> PageResult<T> of(IPage<T> pageResult) {
        return new PageResult<>(pageResult.getCurrent(), pageResult.getSize(),
                pageResult.getTotal(), pageResult.getRecords());
    }

    /** 分页对象 + 元素类型转换器（用于 DTO 映射） */
    public static <S, T> PageResult<T> of(IPage<S> pageResult, Function<S, T> converter) {
        List<T> records = pageResult.getRecords().stream().map(converter).toList();
        return new PageResult<>(pageResult.getCurrent(), pageResult.getSize(),
                pageResult.getTotal(), records);
    }
}