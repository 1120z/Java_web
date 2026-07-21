package com.aviation.controller;

import com.aviation.dao.CategoryDao;
import com.aviation.entity.Category;
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

@WebServlet(name = "CategoryServlet", urlPatterns = "/category")
public class CategoryServlet extends HttpServlet {

    private ObjectMapper objectMapper = new ObjectMapper();
    private CategoryDao categoryDao = new CategoryDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);

        HashMap<String, Object> result = new HashMap<>();
        String action = req.getParameter("action");

        try {
            // 登录校验：未登录拒绝所有操作
            User currentUser = AuthUtil.getLoginUser(req);
            if (currentUser == null) {
                result.put("code", 401);
                result.put("msg", "请先登录");
                resp.getWriter().write(objectMapper.writeValueAsString(result));
                return;
            }

            if ("list".equals(action)) {
                List<Category> list = categoryDao.findAll();
                result.put("code", 200);
                result.put("msg", "查询成功");
                result.put("data", list);

            } else if ("search".equals(action)) {
                String keyword = req.getParameter("keyword");
                List<Category> list = categoryDao.searchByName(keyword);
                result.put("code", 200);
                result.put("msg", "查询成功");
                result.put("data", list);

            } else if ("detail".equals(action)) {
                String idStr = req.getParameter("id");
                if (idStr == null || idStr.trim().isEmpty()) {
                    result.put("code", 400);
                    result.put("msg", "参数 id 不能为空");
                } else {
                    Category category = categoryDao.findById(Integer.parseInt(idStr));
                    result.put("code", 200);
                    result.put("msg", "查询成功");
                    result.put("data", category);
                }

            } else if ("add".equals(action)) {
                InputStream stream = req.getInputStream();
                Category category = objectMapper.readValue(stream, Category.class);
                if (category.getName() == null || category.getName().trim().isEmpty()) {
                    result.put("code", 400);
                    result.put("msg", "分类名称不能为空");
                } else {
                    int rows = categoryDao.add(category);
                    if (rows > 0) {
                        result.put("code", 200);
                        result.put("msg", "添加成功");
                    } else {
                        result.put("code", 400);
                        result.put("msg", "添加失败，分类名可能重复");
                    }
                }

            } else if ("update".equals(action)) {
                InputStream stream = req.getInputStream();
                Category category = objectMapper.readValue(stream, Category.class);
                if (category.getId() == null) {
                    result.put("code", 400);
                    result.put("msg", "参数 id 不能为空");
                } else if (category.getName() == null || category.getName().trim().isEmpty()) {
                    result.put("code", 400);
                    result.put("msg", "分类名称不能为空");
                } else {
                    int rows = categoryDao.update(category);
                    if (rows > 0) {
                        result.put("code", 200);
                        result.put("msg", "修改成功");
                    } else {
                        result.put("code", 400);
                        result.put("msg", "修改失败，分类不存在或名称重复");
                    }
                }

            } else if ("delete".equals(action)) {
                String idStr = req.getParameter("id");
                if (idStr == null || idStr.trim().isEmpty()) {
                    result.put("code", 400);
                    result.put("msg", "参数 id 不能为空");
                } else {
                    int rows = categoryDao.delete(Integer.parseInt(idStr));
                    if (rows > 0) {
                        result.put("code", 200);
                        result.put("msg", "删除成功");
                    } else {
                        result.put("code", 400);
                        result.put("msg", "删除失败，分类不存在或有关联元件");
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

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }
}
