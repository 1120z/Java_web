-- =============================================
-- 航空电子元件库存管理系统 - 数据库初始化脚本
-- 数据库名: aviation_inventory
-- 请在 MySQL 命令行或 Navicat/IDEA 中执行此脚本
-- =============================================

-- 1. 创建数据库
CREATE DATABASE IF NOT EXISTS `aviation_inventory` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `aviation_inventory`;

-- 临时关闭外键检查，避免 DROP 顺序导致重新初始化失败
SET FOREIGN_KEY_CHECKS = 0;

-- 2. 用户表
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
    `id` INT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '登录账号',
    `password` VARCHAR(100) NOT NULL COMMENT '登录密码',
    `real_name` VARCHAR(50) COMMENT '真实姓名',
    `role` VARCHAR(20) DEFAULT 'operator' COMMENT '角色: admin-管理员, operator-操作员',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT '用户表';

-- 3. 元件分类表
DROP TABLE IF EXISTS `categories`;
CREATE TABLE `categories` (
    `id` INT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID',
    `name` VARCHAR(100) NOT NULL COMMENT '分类名称',
    `description` VARCHAR(255) COMMENT '分类描述',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT '元件分类表';

-- 4. 供应商表（必须在 components 之前创建，因为 components 有外键指向它）
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

-- 5. 航空电子元件表（核心表）
DROP TABLE IF EXISTS `components`;
CREATE TABLE `components` (
    `id` INT PRIMARY KEY AUTO_INCREMENT COMMENT '元件ID',
    `part_number` VARCHAR(100) NOT NULL UNIQUE COMMENT '元件编号（唯一）',
    `name` VARCHAR(200) NOT NULL COMMENT '元件名称',
    `category_id` INT COMMENT '所属分类ID',
    `supplier_id` INT COMMENT '所属供应商ID',
    `manufacturer` VARCHAR(200) COMMENT '生产厂家',
    `spec` VARCHAR(255) COMMENT '规格型号',
    `unit` VARCHAR(20) DEFAULT '个' COMMENT '计量单位',
    `stock_quantity` INT DEFAULT 0 COMMENT '当前库存数量',
    `min_stock` INT DEFAULT 10 COMMENT '最低库存预警值',
    `location` VARCHAR(100) COMMENT '存放位置（如：A区-3排-5架）',
    `description` VARCHAR(500) COMMENT '备注说明',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (`category_id`) REFERENCES `categories`(`id`),
    FOREIGN KEY (`supplier_id`) REFERENCES `suppliers`(`id`)
) COMMENT '航空电子元件表';

-- 6. 出入库记录表
DROP TABLE IF EXISTS `inventory_records`;
CREATE TABLE `inventory_records` (
    `id` INT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    `component_id` INT NOT NULL COMMENT '元件ID',
    `type` VARCHAR(20) NOT NULL COMMENT '操作类型: in-入库, out-出库',
    `quantity` INT NOT NULL COMMENT '数量',
    `operator` VARCHAR(50) COMMENT '操作人',
    `remark` VARCHAR(255) COMMENT '备注（如：领用部门、采购单号等）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    FOREIGN KEY (`component_id`) REFERENCES `components`(`id`)
) COMMENT '出入库记录表';

-- =============================================
-- 初始化数据
-- =============================================

-- 管理员账号（密码: admin123）
INSERT INTO `users` (`username`, `password`, `real_name`, `role`) VALUES
('admin', 'admin123', '系统管理员', 'admin'),
('zhangsan', '123456', '张三', 'operator'),
('lisi', '123456', '李四', 'operator');

-- 元件分类
INSERT INTO `categories` (`name`, `description`) VALUES
('航空传感器', '用于飞行姿态、高度、速度等参数检测的传感器类元件'),
('航空连接器', '用于航电系统信号传输和电力供应的各类连接器'),
('航空电路板', '航电系统中的各类印刷电路板（PCB）及组件'),
('航空线缆', '航空专用线缆、线束及屏蔽线'),
('航空仪表', '驾驶舱显示仪表及指示器件'),
('航空电源模块', '航电系统供电及电源管理模块');

-- 供应商（8 家虚构航空工业供应商）
INSERT INTO `suppliers` (`supplier_code`, `name`, `contact_person`, `phone`, `email`, `address`, `level`, `remark`) VALUES
('SUP-001', '中航电子有限公司', '王建国', '010-88561234', 'sales@avic-elec.com', '北京市海淀区学院路37号', 'qualified', '航电系统核心供应商，长期合作'),
('SUP-002', '航天科工集团', '李志强', '010-68371122', 'business@casic.com', '北京市西城区阜成路8号', 'qualified', '军工级电子元器件供应商'),
('SUP-003', '中航光电科技股份有限公司', '张明华', '0379-63385678', 'contact@avic-laser.com', '河南省洛阳市涧西区凯旋西路25号', 'qualified', '军标级连接器专业制造商'),
('SUP-004', '中航计算所', '陈晓东', '029-88453322', 'sales@avic-comp.com', '陕西省西安市高新区科技路38号', 'qualified', '飞控计算机核心板卡供应商'),
('SUP-005', '航天电子有限公司', '刘文海', '021-58881234', 'business@astronics.cn', '上海市闵行区莘庄工业区金都路4388号', 'qualified', '通信管理设备供应商'),
('SUP-006', '航空线缆厂', '赵德柱', '0311-87654321', 'sales@av-cable.com', '河北省石家庄市高新区黄河大道98号', 'qualified', '航空专用线缆专业生产'),
('SUP-007', '航天电源科技有限公司', '孙海军', '0755-83219988', 'contact@aero-power.com', '广东省深圳市南山区科技园南路17号', 'pending', '电源模块供应商，正在评估中'),
('SUP-008', '华丰航空科技有限公司', '周建国', '028-85196677', 'business@huafeng-av.com', '四川省成都市双流区西航港大道189号', 'blacklist', '因交付延期被列入黑名单');

-- 航空电子元件（初始库存，含供应商关联）
INSERT INTO `components` (`part_number`, `name`, `category_id`, `supplier_id`, `manufacturer`, `spec`, `unit`, `stock_quantity`, `min_stock`, `location`, `description`) VALUES
('AVS-001', '高精度气压传感器', 1, 1, '中航电子', 'HT-200A, 精度±0.1hPa', '个', 50, 10, 'A区-1排-3架', '适用于大气数据计算机'),
('AVS-002', '三轴陀螺仪传感器', 1, 2, '航天科工', 'GY-3000, 漂移<0.01°/h', '个', 30, 5, 'A区-1排-4架', '惯性导航系统核心器件'),
('AVS-003', '温度传感器', 1, 1, '中航电子', 'PT100, -50~300°C', '个', 100, 20, 'A区-1排-5架', '发动机温度监测'),
('AVC-001', '圆形航空插头', 2, 3, '中航光电', 'MIL-DTL-38999, 12芯', '个', 200, 50, 'B区-2排-1架', '军标级防水连接器'),
('AVC-002', '矩形航空连接器', 2, 3, '中航光电', 'ARINC 600, 60芯', '个', 80, 15, 'B区-2排-2架', '航电设备背板连接'),
('AVB-001', '飞控计算机主板', 3, 4, '中航计算所', 'FC-200, 双冗余设计', '块', 15, 3, 'C区-3排-1架', '飞行控制核心处理板'),
('AVB-002', '通信管理板', 3, 5, '航天电子', 'CM-100, VHF/UHF', '块', 20, 5, 'C区-3排-2架', '无线电通信管理'),
('AVW-001', '屏蔽双绞线', 4, 6, '航空线缆厂', 'AWG22, 耐温200°C', '米', 500, 100, 'D区-4排-1架', '航电系统信号线缆'),
('AVW-002', '高温线束组件', 4, 6, '航空线缆厂', 'HT-500, 含端头', '套', 60, 10, 'D区-4排-2架', '发动机区域专用线束'),
('AVI-001', '多功能飞行显示器', 5, 1, '中航电子', 'MFD-800, 8英寸LCD', '台', 12, 3, 'E区-5排-1架', '驾驶舱主飞行显示'),
('AVI-002', '发动机参数指示器', 5, 1, '中航电子', 'EPI-400, 4通道', '台', 8, 2, 'E区-5排-2架', '发动机状态监控'),
('AVP-001', '28V直流电源模块', 6, 7, '航天电源', 'DC28-500W, 效率>92%', '台', 25, 5, 'F区-6排-1架', '航电系统主供电');

-- 出入库记录（分布在最近 7 天，让仪表盘折线图有数据）
INSERT INTO `inventory_records` (`component_id`, `type`, `quantity`, `operator`, `remark`, `create_time`) VALUES
-- 6 天前
(1, 'in', 50, 'admin', '首批采购入库，采购单号: PO-2026-001', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(3, 'in', 100, 'admin', '首批采购入库，采购单号: PO-2026-002', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(4, 'out', 10, 'zhangsan', '飞控系统组装领用，工单号: WO-2026-001', DATE_SUB(NOW(), INTERVAL 6 DAY)),
-- 5 天前
(2, 'in', 30, 'admin', '首批采购入库，采购单号: PO-2026-001', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(4, 'out', 20, 'zhangsan', '航电设备装配领用，工单号: WO-2026-003', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(8, 'out', 15, 'lisi', '线束组装领用，工单号: WO-2026-004', DATE_SUB(NOW(), INTERVAL 5 DAY)),
-- 4 天前
(4, 'in', 200, 'zhangsan', '首批采购入库，采购单号: PO-2026-003', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(5, 'in', 80, 'zhangsan', '首批采购入库，采购单号: PO-2026-003', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(1, 'out', 5, 'zhangsan', '飞控系统组装领用，工单号: WO-2026-001', DATE_SUB(NOW(), INTERVAL 4 DAY)),
-- 3 天前
(6, 'in', 15, 'admin', '首批采购入库，采购单号: PO-2026-004', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(7, 'in', 20, 'admin', '首批采购入库，采购单号: PO-2026-004', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(2, 'out', 3, 'zhangsan', '惯导系统组装领用，工单号: WO-2026-002', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(11, 'out', 2, 'admin', '仪表更换领用，工单号: WO-2026-005', DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- 2 天前
(8, 'in', 60, 'lisi', '补货入库，采购单号: PO-2026-005', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9, 'in', 40, 'lisi', '补货入库，采购单号: PO-2026-005', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(8, 'out', 50, 'lisi', '线束组装领用，工单号: WO-2026-004', DATE_SUB(NOW(), INTERVAL 2 DAY)),
-- 1 天前
(10, 'in', 25, 'admin', '补货入库，采购单号: PO-2026-006', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 'out', 8, 'zhangsan', '飞控系统组装领用，工单号: WO-2026-006', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(4, 'out', 15, 'zhangsan', '航电设备装配领用，工单号: WO-2026-007', DATE_SUB(NOW(), INTERVAL 1 DAY)),
-- 今天
(1, 'in', 35, 'admin', '补货入库，采购单号: PO-2026-007', NOW()),
(11, 'in', 5, 'admin', '补货入库，采购单号: PO-2026-007', NOW()),
(8, 'out', 10, 'lisi', '线束组装领用，工单号: WO-2026-008', NOW());

-- 恢复外键检查
SET FOREIGN_KEY_CHECKS = 1;
