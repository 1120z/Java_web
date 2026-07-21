package com.aviation.dao;

import com.aviation.entity.Supplier;
import com.aviation.utils.JDBCUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SupplierDao {

    /**
     * 查询所有供应商
     */
    public List<Supplier> findAll() {
        List<Supplier> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "SELECT id, supplier_code, name, contact_person, phone, email, address, level, remark, create_time FROM suppliers ORDER BY id ASC";
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
     * 多条件搜索：keyword 匹配编号/名称/联系人，level 等级筛选
     */
    public List<Supplier> search(String keyword, String level) {
        List<Supplier> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = JDBCUtils.getConnection();
            StringBuilder sql = new StringBuilder(
                "SELECT id, supplier_code, name, contact_person, phone, email, address, level, remark, create_time FROM suppliers WHERE 1=1"
            );
            List<Object> params = new ArrayList<>();

            if (keyword != null && !keyword.trim().isEmpty()) {
                sql.append(" AND (supplier_code LIKE ? OR name LIKE ? OR contact_person LIKE ?)");
                String pattern = "%" + keyword + "%";
                params.add(pattern);
                params.add(pattern);
                params.add(pattern);
            }
            if (level != null && !level.trim().isEmpty()) {
                sql.append(" AND level = ?");
                params.add(level);
            }

            sql.append(" ORDER BY id ASC");
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
     * 根据 ID 查供应商
     */
    public Supplier findById(int id) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "SELECT id, supplier_code, name, contact_person, phone, email, address, level, remark, create_time FROM suppliers WHERE id = ?";
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
     * 根据编号查重（新增/编辑时用）
     */
    public Supplier findByCode(String supplierCode) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "SELECT id, supplier_code, name, contact_person, phone, email, address, level, remark, create_time FROM suppliers WHERE supplier_code = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, supplierCode);
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
     * 新增供应商
     */
    public int add(Supplier supplier) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "INSERT INTO suppliers (supplier_code, name, contact_person, phone, email, address, level, remark) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, supplier.getSupplierCode());
            pstmt.setString(2, supplier.getName());
            pstmt.setString(3, supplier.getContactPerson());
            pstmt.setString(4, supplier.getPhone());
            pstmt.setString(5, supplier.getEmail());
            pstmt.setString(6, supplier.getAddress());
            pstmt.setString(7, supplier.getLevel());
            pstmt.setString(8, supplier.getRemark());
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            JDBCUtils.close(conn, pstmt);
        }
        return 0;
    }

    /**
     * 修改供应商
     */
    public int update(Supplier supplier) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "UPDATE suppliers SET supplier_code = ?, name = ?, contact_person = ?, phone = ?, email = ?, address = ?, level = ?, remark = ? WHERE id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, supplier.getSupplierCode());
            pstmt.setString(2, supplier.getName());
            pstmt.setString(3, supplier.getContactPerson());
            pstmt.setString(4, supplier.getPhone());
            pstmt.setString(5, supplier.getEmail());
            pstmt.setString(6, supplier.getAddress());
            pstmt.setString(7, supplier.getLevel());
            pstmt.setString(8, supplier.getRemark());
            pstmt.setInt(9, supplier.getId());
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            JDBCUtils.close(conn, pstmt);
        }
        return 0;
    }

    /**
     * 删除供应商
     */
    public int delete(int id) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "DELETE FROM suppliers WHERE id = ?";
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
     * 查询某供应商供应的所有元件
     * 返回字段: part_number, name, manufacturer, stock_quantity, min_stock
     */
    public List<Map<String, Object>> findComponentsBySupplier(int supplierId) {
        List<Map<String, Object>> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = JDBCUtils.getConnection();
            String sql = "SELECT c.part_number, c.name, c.manufacturer, c.stock_quantity, c.min_stock FROM components c WHERE c.supplier_id = ? ORDER BY c.id ASC";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, supplierId);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("partNumber", rs.getString("part_number"));
                item.put("name", rs.getString("name"));
                item.put("manufacturer", rs.getString("manufacturer"));
                item.put("stockQuantity", rs.getInt("stock_quantity"));
                item.put("minStock", rs.getInt("min_stock"));
                list.add(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            JDBCUtils.close(conn, pstmt, rs);
        }
        return list;
    }

    /**
     * 将 ResultSet 当前行映射为 Supplier 对象
     */
    private Supplier mapRow(ResultSet rs) throws SQLException {
        Supplier supplier = new Supplier();
        supplier.setId(rs.getInt("id"));
        supplier.setSupplierCode(rs.getString("supplier_code"));
        supplier.setName(rs.getString("name"));
        supplier.setContactPerson(rs.getString("contact_person"));
        supplier.setPhone(rs.getString("phone"));
        supplier.setEmail(rs.getString("email"));
        supplier.setAddress(rs.getString("address"));
        supplier.setLevel(rs.getString("level"));
        supplier.setRemark(rs.getString("remark"));
        supplier.setCreateTime(rs.getString("create_time"));
        return supplier;
    }
}
