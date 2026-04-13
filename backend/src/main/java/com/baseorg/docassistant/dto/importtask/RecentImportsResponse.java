package com.baseorg.docassistant.dto.importtask;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 最近导入任务列表响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentImportsResponse {

    private List<ImportTaskResponse> tasks;
    private long total;
    private int page;
    private int size;
}
