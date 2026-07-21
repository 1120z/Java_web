package com.aviation.dao;

import com.aviation.entity.Component;
import com.aviation.utils.JDBCUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComponentDao {

    /**
     * 查询所有元件，LEFT JOIN categories 和 suppliers 获取 categoryName 和 supplierName
     */
    public List<Component> findAll() {
        List<Component> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "SELECT c.*, ca.name AS category_name, s.name AS supplier_name FROM components c LEFT JOIN categories ca ON c.category_id = ca.id LEFT JOIN suppliers s ON c.supplier_id = s.id";
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
     * 按元件编号或名称模糊搜索
     */
    public List<Component> search(String keyword) {
        List<Component> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "SELECT c.*, ca.name AS category_name, s.name AS supplier_name FROM components c LEFT JOIN categories ca ON c.category_id = ca.id LEFT JOIN suppliers s ON c.supplier_id = s.id WHERE c.part_number LIKE ? OR c.name LIKE ?";
            pstmt = conn.prepareStatement(sql);
            String pattern = "%" + keyword + "%";
            pstmt.setString(1, pattern);
            pstmt.setString(2, pattern);
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
     * 多条件筛选查询（keyword + categoryId + stockStatus + supplierId）
     * stockStatus: null=全部, "normal"=正常, "low"=低库存
     */
    public List<Component> searchWithFilter(String keyword, Integer categoryId, String stockStatus, Integer supplierId) {
        List<Component> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = JDBCUtils.getConnection();
            StringBuilder sql = new StringBuilder(
                "SELECT c.*, ca.name AS category_name, s.name AS supplier_name FROM components c LEFT JOIN categories ca ON c.category_id = ca.id LEFT JOIN suppliers s ON c.supplier_id = s.id WHERE 1=1"
            );
            List<Object> params = new ArrayList<>();

            if (keyword != null && !keyword.trim().isEmpty()) {
                sql.append(" AND (c.part_number LIKE ? OR c.name LIKE ?)");
                String pattern = "%" + keyword + "%";
                params.add(pattern);
                params.add(pattern);
            }
            if (categoryId != null && categoryId > 0) {
                sql.append(" AND c.category_id = ?");
                params.add(categoryId);
            }
            if ("low".equals(stockStatus)) {
                sql.append(" AND c.stock_quantity < c.min_stock");
            } else if ("normal".equals(stockStatus)) {
                sql.append(" AND c.stock_quantity >= c.min_stock");
            }
            if (supplierId != null && supplierId > 0) {
                sql.append(" AND c.supplier_id = ?");
                params.add(supplierId);
            }

            sql.append(" ORDER BY c.id ASC");
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
     * 按ID查单个元件（含 categoryName 和 supplierName）
     */
    public Component findById(int id) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "SELECT c.*, ca.name AS category_name, s.name AS supplier_name FROM components c LEFT JOIN categories ca ON c.category_id = ca.id LEFT JOIN suppliers s ON c.supplier_id = s.id WHERE c.id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, id);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            JDBCUtils.close(conn, pstmt, rs);
        }
        return null;
    }

    /**
     * 新增元件（不含 id, createTime, updateTime）
     */
    public int add(Component comp) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "INSERT INTO components (part_number, name, category_id, supplier_id, manufacturer, spec, unit, stock_quantity, min_stock, location, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, comp.getPartNumber());
            pstmt.setString(2, comp.getName());
            pstmt.setInt(3, comp.getCategoryId());
            if (comp.getSupplierId() != null) {
                pstmt.setInt(4, comp.getSupplierId());
            } else {
                pstmt.setNull(4, Types.INTEGER);
            }
            pstmt.setString(5, comp.getManufacturer());
            pstmt.setString(6, comp.getSpec());
            pstmt.setString(7, comp.getUnit());
            // 新元件库存统一从 0 开始，实际库存变化必须走出入库事务并生成流水。
            pstmt.setInt(8, 0);
            pstmt.setInt(9, comp.getMinStock());
            pstmt.setString(10, comp.getLocation());
            pstmt.setString(11, comp.getDescription());
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            JDBCUtils.close(conn, pstmt);
        }
        return 0;
    }

    /**
     * 修改元件基础信息。库存由出入库事务维护，此处不会覆盖 stock_quantity。
     */
    public int update(Component comp) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "UPDATE components SET part_number = ?, name = ?, category_id = ?, supplier_id = ?, " +
                    "manufacturer = ?, spec = ?, unit = ?, min_stock = ?, location = ?, description = ? " +
                    "WHERE id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, comp.getPartNumber());
            pstmt.setString(2, comp.getName());
            pstmt.setInt(3, comp.getCategoryId());
            if (comp.getSupplierId() != null) {
                pstmt.setInt(4, comp.getSupplierId());
            } else {
                pstmt.setNull(4, Types.INTEGER);
            }
            pstmt.setString(5, comp.getManufacturer());
            pstmt.setString(6, comp.getSpec());
            pstmt.setString(7, comp.getUnit());
            pstmt.setInt(8, comp.getMinStock());
            pstmt.setString(9, comp.getLocation());
            pstmt.setString(10, comp.getDescription());
            pstmt.setInt(11, comp.getId());
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            JDBCUtils.close(conn, pstmt);
        }
        return 0;
    }

    /**
     * 删除元件
     */
    public int delete(int id) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "DELETE FROM components WHERE id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, id);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            JDBCUtils.close(conn, pstmt);
        }
        return 0;
    }

    /**
     * 更新库存数量（增量更新：stock_quantity = stock_quantity + ?）
     * quantity > 0 入库，quantity < 0 出库
     * SQL 条件保证并发调用时更新后的库存也不会小于 0。
     */
    public int updateStock(int id, int quantity) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "UPDATE components SET stock_quantity = stock_quantity + ? " +
                    "WHERE id = ? AND stock_quantity + ? >= 0";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, quantity);
            pstmt.setInt(2, id);
            pstmt.setInt(3, quantity);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            JDBCUtils.close(conn, pstmt);
        }
        return 0;
    }

    /**
     * 查询低库存预警元件（stock_quantity < min_stock）
     */
    public List<Component> findLowStock() {
        List<Component> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "SELECT c.*, ca.name AS category_name, s.name AS supplier_name FROM components c LEFT JOIN categories ca ON c.category_id = ca.id LEFT JOIN suppliers s ON c.supplier_id = s.id WHERE c.stock_quantity < c.min_stock";
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
     * 统计信息：总元件数、总库存量、低库存预警数、分类数
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> map = new HashMap<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "SELECT COUNT(*) AS total_count, IFNULL(SUM(stock_quantity), 0) AS total_stock, SUM(CASE WHEN stock_quantity < min_stock THEN 1 ELSE 0 END) AS low_stock_count FROM components";
            pstmt = conn.prepareStatement(sql);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                map.put("totalCount", rs.getInt("total_count"));
                map.put("totalStock", rs.getInt("total_stock"));
                map.put("lowStockCount", rs.getInt("low_stock_count"));
            }

            // 查询分类数
            String sql2 = "SELECT COUNT(*) AS category_count FROM categories";
            pstmt.close();
            pstmt = conn.prepareStatement(sql2);
            rs.close();
            rs = pstmt.executeQuery();
            if (rs.next()) {
                map.put("categoryCount", rs.getInt("category_count"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            JDBCUtils.close(conn, pstmt, rs);
        }
        return map;
    }

    /**
     * 将 ResultSet 当前行映射为 Component 对象
     */
    private Component mapRow(ResultSet rs) throws SQLException {
        Component comp = new Component();
        comp.setId(rs.getInt("id"));
        comp.setPartNumber(rs.getString("part_number"));
        comp.setName(rs.getString("name"));
        comp.setCategoryId(rs.getInt("category_id"));
        comp.setCategoryName(rs.getString("category_name"));
        comp.setSupplierId(rs.getObject("supplier_id") != null ? rs.getInt("supplier_id") : null);
        comp.setSupplierName(rs.getString("supplier_name"));
        comp.setManufacturer(rs.getString("manufacturer"));
        comp.setSpec(rs.getString("spec"));
        comp.setUnit(rs.getString("unit"));
        comp.setStockQuantity(rs.getInt("stock_quantity"));
        comp.setMinStock(rs.getInt("min_stock"));
        comp.setLocation(rs.getString("location"));
        comp.setDescription(rs.getString("description"));
        comp.setCreateTime(rs.getString("create_time"));
        comp.setUpdateTime(rs.getString("update_time"));
        return comp;
    }

    /**
     * 按分类统计元件数量（环形图用）
     */
    public List<Map<String, Object>> countByCategory() {
        List<Map<String, Object>> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "SELECT ca.name AS category_name, COUNT(*) AS count FROM components c JOIN categories ca ON c.category_id = ca.id GROUP BY ca.id, ca.name ORDER BY count DESC";
            pstmt = conn.prepareStatement(sql);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", rs.getString("category_name"));
                item.put("value", rs.getInt("count"));
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
