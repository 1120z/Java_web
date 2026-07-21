package com.aviation.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class InventoryRecord {
    private Integer id;
    private Integer componentId;
    private String partNumber;     // 关联查询用
    private String componentName;  // 关联查询用
    private String type;           // in-入库, out-出库
    private Integer quantity;
    private String operator;
    private String remark;
    private String createTime;
}
