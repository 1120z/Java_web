package com.aviation.dao;

import com.aviation.entity.InventoryRecord;
import com.aviation.utils.JDBCUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryRecordDao {

    /**
     * 查询所有记录，LEFT JOIN components 获取 part_number 和 name
     */
    public List<InventoryRecord> findAll() {
        List<InventoryRecord> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "SELECT r.*, c.part_number, c.name AS component_name " +
                    "FROM inventory_records r " +
                    "LEFT JOIN components c ON r.component_id = c.id " +
                    "ORDER BY r.create_time DESC, r.id DESC";
            pstmt = conn.prepareStatement(sql);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            JDBCUtils.close(conn, pstmt, rs);
        }
        return list;
    }

    /**
     * 查某元件的出入库记录
     */
    public List<InventoryRecord> findByComponentId(int componentId) {
        List<InventoryRecord> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "SELECT r.*, c.part_number, c.name AS component_name " +
                    "FROM inventory_records r " +
                    "LEFT JOIN components c ON r.component_id = c.id " +
                    "WHERE r.component_id = ? " +
                    "ORDER BY r.create_time DESC, r.id DESC";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, componentId);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            JDBCUtils.close(conn, pstmt, rs);
        }
        return list;
    }

    /**
     * 在同一个事务中完成库存行锁定、库存校验、库存更新和流水写入。
     * SELECT ... FOR UPDATE 会串行化同一元件的并发出库，防止库存被扣成负数。
     */
    public OperationResult addAndUpdateStock(InventoryRecord record) {
        OperationResult validationResult = validate(record);
        if (validationResult != null) {
            return validationResult;
        }

        try (Connection conn = JDBCUtils.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer currentStock = lockCurrentStock(conn, record.getComponentId());
                if (currentStock == null) {
                    conn.rollback();
                    return OperationResult.componentNotFound();
                }

                long updatedStock = "in".equals(record.getType())
                        ? (long) currentStock + record.getQuantity()
                        : (long) currentStock - record.getQuantity();

                if (updatedStock < 0) {
                    conn.rollback();
                    return OperationResult.insufficientStock(currentStock);
                }
                if (updatedStock > Integer.MAX_VALUE) {
                    conn.rollback();
                    return OperationResult.invalidRequest("入库后库存数量超出系统允许范围");
                }

                updateStock(conn, record.getComponentId(), (int) updatedStock);
                insertRecord(conn, record);
                conn.commit();
                return OperationResult.success((int) updatedStock);
            } catch (SQLException | RuntimeException e) {
                rollback(conn, e);
                e.printStackTrace();
                return OperationResult.error();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return OperationResult.error();
        }
    }

    private OperationResult validate(InventoryRecord record) {
        if (record == null || record.getComponentId() == null || record.getComponentId() <= 0) {
            return OperationResult.invalidRequest("参数 componentId 必须是正整数");
        }
        if (!"in".equals(record.getType()) && !"out".equals(record.getType())) {
            return OperationResult.invalidRequest("type 必须是 in 或 out");
        }
        if (record.getQuantity() == null || record.getQuantity() <= 0) {
            return OperationResult.invalidRequest("quantity 必须是正整数");
        }
        return null;
    }

    private Integer lockCurrentStock(Connection conn, int componentId) throws SQLException {
        String sql = "SELECT stock_quantity FROM components WHERE id = ? FOR UPDATE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, componentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                int currentStock = rs.getInt("stock_quantity");
                return rs.wasNull() ? 0 : currentStock;
            }
        }
    }

    private void updateStock(Connection conn, int componentId, int updatedStock) throws SQLException {
        String sql = "UPDATE components SET stock_quantity = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, updatedStock);
            pstmt.setInt(2, componentId);
            if (pstmt.executeUpdate() != 1) {
                throw new SQLException("库存更新失败");
            }
        }
    }

    private void insertRecord(Connection conn, InventoryRecord record) throws SQLException {
        String sql = "INSERT INTO inventory_records " +
                "(component_id, type, quantity, operator, remark) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, record.getComponentId());
            pstmt.setString(2, record.getType());
            pstmt.setInt(3, record.getQuantity());
            pstmt.setString(4, record.getOperator());
            pstmt.setString(5, record.getRemark());
            if (pstmt.executeUpdate() != 1) {
                throw new SQLException("出入库记录写入失败");
            }
        }
    }

    private void rollback(Connection conn, Exception originalException) {
        try {
            conn.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }

    public enum OperationStatus {
        SUCCESS,
        COMPONENT_NOT_FOUND,
        INSUFFICIENT_STOCK,
        INVALID_REQUEST,
        ERROR
    }

    public static final class OperationResult {
        private final OperationStatus status;
        private final Integer stockQuantity;
        private final String message;

        private OperationResult(OperationStatus status, Integer stockQuantity, String message) {
            this.status = status;
            this.stockQuantity = stockQuantity;
            this.message = message;
        }

        public static OperationResult success(int stockQuantity) {
            return new OperationResult(OperationStatus.SUCCESS, stockQuantity, null);
        }

        public static OperationResult componentNotFound() {
            return new OperationResult(OperationStatus.COMPONENT_NOT_FOUND, null, "元件不存在");
        }

        public static OperationResult insufficientStock(int currentStock) {
            return new OperationResult(OperationStatus.INSUFFICIENT_STOCK, currentStock, "库存不足");
        }

        public static OperationResult invalidRequest(String message) {
            return new OperationResult(OperationStatus.INVALID_REQUEST, null, message);
        }

        public static OperationResult error() {
            return new OperationResult(OperationStatus.ERROR, null, "出入库操作失败");
        }

        public OperationStatus getStatus() {
            return status;
        }

        public Integer getStockQuantity() {
            return stockQuantity;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 多条件筛选查询（componentId + type + 日期范围）
     */
    public List<InventoryRecord> search(Integer componentId, String type, String startDate, String endDate) {
        List<InventoryRecord> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = JDBCUtils.getConnection();
            StringBuilder sql = new StringBuilder(
                "SELECT r.*, c.part_number, c.name AS component_name FROM inventory_records r LEFT JOIN components c ON r.component_id = c.id WHERE 1=1"
            );
            List<Object> params = new ArrayList<>();

            if (componentId != null && componentId > 0) {
                sql.append(" AND r.component_id = ?");
                params.add(componentId);
            }
            if (type != null && !type.trim().isEmpty()) {
                sql.append(" AND r.type = ?");
                params.add(type);
            }
            if (startDate != null && !startDate.trim().isEmpty()) {
                sql.append(" AND DATE(r.create_time) >= ?");
                params.add(startDate);
            }
            if (endDate != null && !endDate.trim().isEmpty()) {
                sql.append(" AND DATE(r.create_time) <= ?");
                params.add(endDate);
            }

            sql.append(" ORDER BY r.create_time DESC, r.id DESC");
            pstmt = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String) {
                    pstmt.setString(i + 1, (String) p);
                } else if (p instanceof Integer) {
                    pstmt.setInt(i + 1, (Integer) p);
                }
            }
            rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            JDBCUtils.close(conn, pstmt, rs);
        }
        return list;
    }

    /**
     * 将 ResultSet 当前行映射为 InventoryRecord 对象
     */
    private InventoryRecord mapRow(ResultSet rs) throws SQLException {
        InventoryRecord record = new InventoryRecord();
        record.setId(rs.getInt("id"));
        record.setComponentId(rs.getInt("component_id"));
        record.setPartNumber(rs.getString("part_number"));
        record.setComponentName(rs.getString("component_name"));
        record.setType(rs.getString("type"));
        record.setQuantity(rs.getInt("quantity"));
        record.setOperator(rs.getString("operator"));
        record.setRemark(rs.getString("remark"));
        record.setCreateTime(rs.getString("create_time"));
        return record;
    }

    /**
     * 统计最近 N 天每天的入库/出库总量（折线图用）
     * 返回字段: date, inTotal, outTotal
     */
    public List<Map<String, Object>> countByDay(int days) {
        List<Map<String, Object>> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "SELECT DATE(create_time) AS date, " +
                    "SUM(CASE WHEN type = 'in' THEN quantity ELSE 0 END) AS in_total, " +
                    "SUM(CASE WHEN type = 'out' THEN quantity ELSE 0 END) AS out_total " +
                    "FROM inventory_records " +
                    "WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
                    "GROUP BY DATE(create_time) " +
                    "ORDER BY date ASC";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, days);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("date", rs.getString("date"));
                item.put("inTotal", rs.getInt("in_total"));
                item.put("outTotal", rs.getInt("out_total"));
                list.add(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            JDBCUtils.close(conn, pstmt, rs);
        }
        return list;
    }
}
