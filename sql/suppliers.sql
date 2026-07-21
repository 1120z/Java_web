-- =============================================
-- 供应商管理模块 - 数据库迁移脚本
-- 执行前请确保 aviation_inventory 数据库已存在并已执行过 init.sql
-- 在 MySQL 命令行或 Navicat/IDEA 中执行此脚本
-- =============================================

USE `aviation_inventory`;

-- 1. 供应商表
DROP TABLE IF EXISTS `suppliers`;
CREATE TABLE `suppliers` (
    `id` INT PRIMARY KEY AUTO_INCREMENT COMMENT '供应商ID',
    `supplier_code` VARCHAR(50) NOT NULL UNIQUE COMMENT '供应商编号（唯一）',
    `name` VARCHAR(200) NOT NULL COMMENT '供应商名称',
    `contact_person` VARCHAR(50) COMMENT '联系人',
    `phone` VARCHAR(50) COMMENT '联系电话',
    `email` VARCHAR(100) COMMENT '邮箱',
    `address` VARCHAR(255) COMMENT '地址',
    `level` VARCHAR(20) DEFAULT 'qualified' COMMENT '等级: qualified-合格, pending-待评估, blacklist-黑名单',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT '供应商表';

-- 2. 给 components 表加 supplier_id 字段
ALTER TABLE `components` ADD COLUMN `supplier_id` INT COMMENT '供应商ID' AFTER `category_id`;
ALTER TABLE `components` ADD CONSTRAINT `fk_component_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers`(`id`);

-- 3. 初始化供应商数据（航空工业供应商，8 家虚构公司）
INSERT INTO `suppliers` (`supplier_code`, `name`, `contact_person`, `phone`, `email`, `address`, `level`, `remark`) VALUES
('SUP-001', '中航电子有限公司', '王建国', '010-88561234', 'sales@avic-elec.com', '北京市海淀区学院路37号', 'qualified', '航电系统核心供应商，长期合作'),
('SUP-002', '航天科工集团', '李志强', '010-68371122', 'business@casic.com', '北京市西城区阜成路8号', 'qualified', '军工级电子元器件供应商'),
('SUP-003', '中航光电科技股份有限公司', '张明华', '0379-63385678', 'contact@avic-laser.com', '河南省洛阳市涧西区凯旋西路25号', 'qualified', '军标级连接器专业制造商'),
('SUP-004', '中航计算所', '陈晓东', '029-88453322', 'sales@avic-comp.com', '陕西省西安市高新区科技路38号', 'qualified', '飞控计算机核心板卡供应商'),
('SUP-005', '航天电子有限公司', '刘文海', '021-58881234', 'business@astronics.cn', '上海市闵行区莘庄工业区金都路4388号', 'qualified', '通信管理设备供应商'),
('SUP-006', '航空线缆厂', '赵德柱', '0311-87654321', 'sales@av-cable.com', '河北省石家庄市高新区黄河大道98号', 'qualified', '航空专用线缆专业生产'),
('SUP-007', '航天电源科技有限公司', '孙海军', '0755-83219988', 'contact@aero-power.com', '广东省深圳市南山区科技园南路17号', 'pending', '电源模块供应商，正在评估中'),
('SUP-008', '华丰航空科技有限公司', '周建国', '028-85196677', 'business@huafeng-av.com', '四川省成都市双流区西航港大道189号', 'blacklist', '因交付延期被列入黑名单');

-- 4. 给现有 12 个元件分配供应商
-- AVS-001 ~ AVS-003 传感器 -> SUP-001 中航电子
UPDATE `components` SET `supplier_id` = 1 WHERE `part_number` IN ('AVS-001', 'AVS-002', 'AVS-003');

-- AVC-001 ~ AVC-002 连接器 -> SUP-003 中航光电
UPDATE `components` SET `supplier_id` = 3 WHERE `part_number` IN ('AVC-001', 'AVC-002');

-- AVB-001 ~ AVB-002 电路板 -> SUP-004 中航计算所 + SUP-005 航天电子
UPDATE `components` SET `supplier_id` = 4 WHERE `part_number` = 'AVB-001';
UPDATE `components` SET `supplier_id` = 5 WHERE `part_number` = 'AVB-002';

-- AVW-001 ~ AVW-002 线缆 -> SUP-006 航空线缆厂
UPDATE `components` SET `supplier_id` = 6 WHERE `part_number` IN ('AVW-001', 'AVW-002');

-- AVI-001 ~ AVI-002 仪表 -> SUP-001 中航电子
UPDATE `components` SET `supplier_id` = 1 WHERE `part_number` IN ('AVI-001', 'AVI-002');

-- AVP-001 电源模块 -> SUP-007 航天电源（待评估供应商）
UPDATE `components` SET `supplier_id` = 7 WHERE `part_number` = 'AVP-001';

-- =============================================
-- 验证: 执行完后用以下 SQL 检查
-- =============================================
-- SELECT s.supplier_code, s.name, COUNT(c.id) AS component_count
-- FROM suppliers s LEFT JOIN components c ON s.id = c.supplier_id
-- GROUP BY s.id ORDER BY s.id;
-- 预期结果: 8 家供应商，SUP-001 有 5 个元件最多，SUP-008 黑名单 0 个元件
