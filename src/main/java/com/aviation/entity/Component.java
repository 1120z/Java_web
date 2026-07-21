package com.aviation.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Component {
    private Integer id;
    private String partNumber;
    private String name;
    private Integer categoryId;
    private String categoryName;   // 关联查询用，非数据库字段
    private Integer supplierId;
    private String supplierName;    // 关联查询用，非数据库字段
    private String manufacturer;
    private String spec;
    private String unit;
    private Integer stockQuantity;
    private Integer minStock;
    private String location;
    private String description;
    private String createTime;
    private String updateTime;
}
