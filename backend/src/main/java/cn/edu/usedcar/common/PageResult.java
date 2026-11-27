package cn.edu.usedcar.common;

import java.util.List;
import java.util.function.Function;

/**
 * 分页结果。转换页内元素时保留总数、页码与每页条数。
 * @author pangzhihao
 */
public record PageResult<T>(List<T> items, long total, int page, int pageSize) {
    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), total, page, pageSize);
    }
}
