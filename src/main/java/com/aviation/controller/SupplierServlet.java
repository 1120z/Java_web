package com.aviation.controller;

import com.aviation.dao.SupplierDao;
import com.aviation.entity.Supplier;
import com.aviation.entity.User;
import com.aviation.utils.AuthUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "SupplierServlet", urlPatterns = "/supplier")
public class SupplierServlet extends HttpServlet {

    private ObjectMapper objectMapper = new ObjectMapper();
    private SupplierDao supplierDao = new SupplierDao();
    private static final List<String> VALID_LEVELS = Arrays.asList("qualified", "pending", "blacklist");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        HashMap<String, Object> result = new HashMap<>();
        String action = req.getParameter("action");

        try {
            User currentUser = AuthUtil.getLoginUser(req);
            if (currentUser == null) {
                result.put("code", 401);
                result.put("msg", "请先登录");
                resp.getWriter().write(objectMapper.writeValueAsString(result));
                return;
            }

            if ("list".equals(action)) {
                List<Supplier> list = supplierDao.findAll();
                result.put("code", 200);
                result.put("msg", "查询成功");
                result.put("data", list);

            } else if ("search".equals(action)) {
                String keyword = req.getParameter("keyword");
                String level = req.getParameter("level");
                List<Supplier> list = supplierDao.search(keyword, level);
                result.put("code", 200);
                result.put("msg", "查询成功");
                result.put("data", list);

            } else if ("detail".equals(action)) {
                String idStr = req.getParameter("id");
                if (idStr == null || idStr.trim().isEmpty()) {
                    result.put("code", 400);
                    result.put("msg", "参数 id 不能为空");
                } else {
                    Supplier supplier = supplierDao.findById(Integer.parseInt(idStr));
                    result.put("code", 200);
                    result.put("msg", "查询成功");
                    result.put("data", supplier);
                }

            } else if ("components".equals(action)) {
                String idStr = req.getParameter("id");
                if (idStr == null || idStr.trim().isEmpty()) {
                    result.put("code", 400);
                    result.put("msg", "参数 id 不能为空");
                } else {
                    List<Map<String, Object>> components = supplierDao.findComponentsBySupplier(Integer.parseInt(idStr));
                    result.put("code", 200);
                    result.put("msg", "查询成功");
                    result.put("data", components);
                }

            } else if ("delete".equals(action)) {
                String idStr = req.getParameter("id");
                if (idStr == null || idStr.trim().isEmpty()) {
                    result.put("code", 400);
                    result.put("msg", "参数 id 不能为空");
                } else {
                    int rows = supplierDao.delete(Integer.parseInt(idStr));
                    if (rows > 0) {
                        result.put("code", 200);
                        result.put("msg", "删除成功");
                    } else {
                        result.put("code", 400);
                        result.put("msg", "删除失败，供应商不存在或有关联元件");
                    }
                }

            } else if ("add".equals(action) || "update".equals(action)) {
                doPost(req, resp);
                return;

            } else {
                result.put("code", 400);
                result.put("msg", "未知操作");
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", "服务器内部错误");
        }

        resp.getWriter().write(objectMapper.writeValueAsString(result));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        HashMap<String, Object> result = new HashMap<>();
        String action = req.getParameter("action");

        try {
            User currentUser = AuthUtil.getLoginUser(req);
            if (currentUser == null) {
                result.put("code", 401);
                result.put("msg", "请先登录");
                resp.getWriter().write(objectMapper.writeValueAsString(result));
                return;
            }

            if ("add".equals(action)) {
                InputStream stream = req.getInputStream();
                Supplier supplier = objectMapper.readValue(stream, Supplier.class);

                if (supplier.getSupplierCode() == null || supplier.getSupplierCode().trim().isEmpty()
                        || supplier.getName() == null || supplier.getName().trim().isEmpty()) {
                    result.put("code", 400);
                    result.put("msg", "供应商编号和名称不能为空");
                } else if (supplierDao.findByCode(supplier.getSupplierCode()) != null) {
                    result.put("code", 400);
                    result.put("msg", "供应商编号已存在");
                } else if (supplier.getLevel() != null && !supplier.getLevel().isEmpty()
                        && !VALID_LEVELS.contains(supplier.getLevel())) {
                    result.put("code", 400);
                    result.put("msg", "level 必须是 qualified / pending / blacklist 之一");
                } else {
                    if (supplier.getLevel() == null || supplier.getLevel().isEmpty()) {
                        supplier.setLevel("qualified");
                    }
                    int rows = supplierDao.add(supplier);
                    if (rows > 0) {
                        result.put("code", 200);
                        result.put("msg", "添加成功");
                    } else {
                        result.put("code", 500);
                        result.put("msg", "添加失败");
                    }
                }

            } else if ("update".equals(action)) {
                InputStream stream = req.getInputStream();
                Supplier supplier = objectMapper.readValue(stream, Supplier.class);

                if (supplier.getId() == null) {
                    result.put("code", 400);
                    result.put("msg", "参数 id 不能为空");
                } else if (supplier.getSupplierCode() == null || supplier.getSupplierCode().trim().isEmpty()
                        || supplier.getName() == null || supplier.getName().trim().isEmpty()) {
                    result.put("code", 400);
                    result.put("msg", "供应商编号和名称不能为空");
                } else if (supplier.getLevel() != null && !supplier.getLevel().isEmpty()
                        && !VALID_LEVELS.contains(supplier.getLevel())) {
                    result.put("code", 400);
                    result.put("msg", "level 必须是 qualified / pending / blacklist 之一");
                } else {
                    Supplier existing = supplierDao.findByCode(supplier.getSupplierCode());
                    if (existing != null && !existing.getId().equals(supplier.getId())) {
                        result.put("code", 400);
                        result.put("msg", "供应商编号已被其他记录占用");
                    } else {
                        int rows = supplierDao.update(supplier);
                        if (rows > 0) {
                            result.put("code", 200);
                            result.put("msg", "修改成功");
                        } else {
                            result.put("code", 400);
                            result.put("msg", "修改失败，供应商不存在");
                        }
                    }
                }

            } else {
                result.put("code", 400);
                result.put("msg", "未知操作");
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", "服务器内部错误");
        }

        resp.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
