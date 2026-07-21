-- =============================================
-- 仪表盘折线图数据修复脚本（增量）
-- 作用：把出入库记录的 create_time 分散到最近 7 天
-- 使用场景：数据库已经初始化过，但仪表盘折线图为空
-- 使用方法：在 Navicat 中连接 aviation_inventory 库后执行整个脚本
-- =============================================

USE `aviation_inventory`;

-- 1. 清空现有的出入库记录
DELETE FROM `inventory_records`;

-- 2. 重新插入分散到最近 7 天的记录
-- 临时关闭外键检查，避免 component_id 外键校验问题
SET FOREIGN_KEY_CHECKS = 0;

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

SET FOREIGN_KEY_CHECKS = 1;

-- 3. 验证一下结果（可选，执行后会在 Navicat 结果区显示）
SELECT
    DATE(create_time) AS 日期,
    SUM(CASE WHEN type = 'in' THEN quantity ELSE 0 END) AS 入库总量,
    SUM(CASE WHEN type = 'out' THEN quantity ELSE 0 END) AS 出库总量,
    COUNT(*) AS 记录数
FROM inventory_records
WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
GROUP BY DATE(create_time)
ORDER BY 日期 ASC;
