package com.faust.easychat.result;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class PageResult<T> {

    private List<T> records;

    private long total;

    private long page;

    private long size;

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<T>()
                .setRecords(records)
                .setTotal(total)
                .setPage(page)
                .setSize(size);
    }


}
