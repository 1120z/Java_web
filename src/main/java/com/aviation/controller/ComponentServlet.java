package com.aviation.controller;

import com.aviation.dao.ComponentDao;
import com.aviation.entity.Component;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "ComponentServlet", urlPatterns = "/component")
public class ComponentServlet extends HttpServlet {

    private ObjectMapper objectMapper = new ObjectMapper();
    private ComponentDao componentDao = new ComponentDao();

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
                List<Component> list = componentDao.findAll();
                result.put("code", 200);
                result.put("msg", "查询成功");
                result.put("data", list);

            } else if ("search".equals(action)) {
                String keyword = req.getParameter("keyword");
                String categoryIdStr = req.getParameter("categoryId");
                String stockStatus = req.getParameter("stockStatus");
                String supplierIdStr = req.getParameter("supplierId");
                Integer categoryId = (categoryIdStr != null && !categoryIdStr.isEmpty()) ? Integer.parseInt(categoryIdStr) : null;
                Integer supplierId = (supplierIdStr != null && !supplierIdStr.isEmpty()) ? Integer.parseInt(supplierIdStr) : null;
                List<Component> list = componentDao.searchWithFilter(keyword, categoryId, stockStatus, supplierId);
                result.put("code", 200);
                result.put("msg", "查询成功");
                result.put("data", list);

            } else if ("detail".equals(action)) {
                String idStr = req.getParameter("id");
                if (idStr == null || idStr.trim().isEmpty()) {
                    result.put("code", 400);
                    result.put("msg", "参数 id 不能为空");
                } else {
                    Component component = componentDao.findById(Integer.parseInt(idStr));
                    result.put("code", 200);
                    result.put("msg", "查询成功");
                    result.put("data", component);
                }

            } else if ("lowstock".equals(action)) {
                List<Component> list = componentDao.findLowStock();
                result.put("code", 200);
                result.put("msg", "查询成功");
                result.put("data", list);

            } else if ("stats".equals(action)) {
                Map<String, Object> stats = componentDao.getStatistics();
                result.put("code", 200);
                result.put("msg", "查询成功");
                result.put("data", stats);

            } else if ("add".equals(action)) {
                InputStream stream = req.getInputStream();
                Component component = objectMapper.readValue(stream, Component.class);
                if (component.getPartNumber() == null || component.getPartNumber().trim().isEmpty()
                        || component.getName() == null || component.getName().trim().isEmpty()) {
                    result.put("code", 400);
                    result.put("msg", "元件编号和名称不能为空");
                } else if (component.getStockQuantity() != null && component.getStockQuantity() < 0) {
                    result.put("code", 400);
                    result.put("msg", "库存数量不能为负");
                } else if (component.getStockQuantity() != null && component.getStockQuantity() > 0) {
                    result.put("code", 400);
                    result.put("msg", "新增元件的初始库存必须为 0，请在创建后通过出入库管理执行入库");
                } else {
                    component.setStockQuantity(0);
                    int rows = componentDao.add(component);
                    if (rows > 0) {
                        result.put("code", 200);
                        result.put("msg", "添加成功");
                    } else {
                        result.put("code", 400);
                        result.put("msg", "添加失败，编号可能重复");
                    }
                }

            } else if ("update".equals(action)) {
                InputStream stream = req.getInputStream();
                Component component = objectMapper.readValue(stream, Component.class);
                if (component.getId() == null) {
                    result.put("code", 400);
                    result.put("msg", "参数 id 不能为空");
                } else if (component.getPartNumber() == null || component.getPartNumber().trim().isEmpty()
                        || component.getName() == null || component.getName().trim().isEmpty()) {
                    result.put("code", 400);
                    result.put("msg", "元件编号和名称不能为空");
                } else {
                    int rows = componentDao.update(component);
                    if (rows > 0) {
                        result.put("code", 200);
                        result.put("msg", "修改成功");
                    } else {
                        result.put("code", 400);
                        result.put("msg", "修改失败，元件不存在或编号重复");
                    }
                }

            } else if ("delete".equals(action)) {
                String idStr = req.getParameter("id");
                if (idStr == null || idStr.trim().isEmpty()) {
                    result.put("code", 400);
                    result.put("msg", "参数 id 不能为空");
                } else {
                    int rows = componentDao.delete(Integer.parseInt(idStr));
                    if (rows > 0) {
                        result.put("code", 200);
                        result.put("msg", "删除成功");
                    } else {
                        result.put("code", 400);
                        result.put("msg", "删除失败，元件不存在或有关联记录");
                    }
                }

            } else if ("updatestock".equals(action)) {
                result.put("code", 400);
                result.put("msg", "不允许直接修改库存，请使用 /record?action=add 完成出入库操作");

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
        doGet(req, resp);
    }
}
