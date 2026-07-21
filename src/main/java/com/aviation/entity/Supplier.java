package com.aviation.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Supplier {
    private Integer id;
    private String supplierCode;
    private String name;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private String level;       // qualified-合格, pending-待评估, blacklist-黑名单
    private String remark;
    private String createTime;
}
