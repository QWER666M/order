package com.example.order.dto;

import java.util.List;
import java.util.function.Function;

/**
 * 通用分页响应结构。
 *
 * 为什么不直接返回整个 List？
 *   数据一多，一次性全查出来会占大量内存、响应变慢，极端情况把服务拖垮（OOM）。
 *   分页让客户端"一页一页拿"，每次只取固定条数。
 *
 * 泛型 <T>：表示"这一页里装的是什么"。所以它既能装 Order（业务层用），
 * 也能装 OrderResponse（接口层返回给外部用）—— 一份结构，两种用途。
 *
 * 页码约定：page 从 1 开始（对人友好）。
 *   （Spring Data 等框架习惯从 0 开始，两种都可以，关键是项目内统一并写进文档。）
 */
public record PageResponse<T>(
        List<T> items,      // 当前页的数据
        long total,         // 总记录数（用 long：记录数可能超过 int 上限）
        int page,           // 当前页码，从 1 开始
        int size            // 每页条数
) {

    /**
     * 总页数：向上取整。
     * 例如 25 条、每页 10 条 -> (25 + 10 - 1) / 10 = 3 页。
     *
     * ★ 方法名用 getTotalPages 而不是 totalPages：
     *   Jackson 序列化时按 JavaBean 约定识别 getXxx() 方法，
     *   叫 totalPages() 的话不会出现在 JSON 里。
     */
    public int getTotalPages() {
        return (int) ((total + size - 1) / size);
    }

    /**
     * 把本页的每个元素转换成另一种类型，其余分页信息原样保留。
     * 例如：PageResponse&lt;Order&gt; -> PageResponse&lt;OrderResponse&gt;
     */
    public <R> PageResponse<R> map(Function<T, R> mapper) {
        return new PageResponse<>(
                items.stream().map(mapper).toList(),
                total, page, size);
    }
}
