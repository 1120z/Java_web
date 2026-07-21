package com.aviation.controller;

import com.aviation.dao.ComponentDao;
import com.aviation.dao.InventoryRecordDao;
import com.aviation.entity.Component;
import com.aviation.entity.InventoryRecord;
import com.aviation.entity.User;
import com.aviation.utils.AuthUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "DashboardServlet", urlPatterns = "/dashboard")
public class DashboardServlet extends HttpServlet {

    private ObjectMapper objectMapper = new ObjectMapper();
    private ComponentDao componentDao = new ComponentDao();
    private InventoryRecordDao inventoryRecordDao = new InventoryRecordDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        // 仪表盘数据会随出入库操作变化，禁止浏览器或中间缓存复用旧响应。
        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);

        HashMap<String, Object> result = new HashMap<>();

        try {
            // 登录校验
            User currentUser = AuthUtil.getLoginUser(req);
            if (currentUser == null) {
                result.put("code", 401);
                result.put("msg", "请先登录");
                resp.getWriter().write(objectMapper.writeValueAsString(result));
                return;
            }

            // 获取统计数据
            Map<String, Object> stats = componentDao.getStatistics();

            // 获取最近10条出入库记录
            List<InventoryRecord> allRecords = inventoryRecordDao.findAll();
            List<InventoryRecord> recentRecords;
            if (allRecords.size() > 10) {
                recentRecords = allRecords.subList(0, 10);
            } else {
                recentRecords = allRecords;
            }

            // 获取低库存预警列表
            List<Component> lowStockList = componentDao.findLowStock();

            // 获取环形图数据: 各分类元件数量
            List<Map<String, Object>> categoryStats = componentDao.countByCategory();

            // 获取折线图数据: 近7天出入库趋势
            List<Map<String, Object>> trend7d = inventoryRecordDao.countByDay(7);

            // 全部封装在一个 HashMap 里返回
            HashMap<String, Object> data = new HashMap<>();
            data.put("statistics", stats);
            data.put("recentRecords", recentRecords);
            data.put("lowStockList", lowStockList);
            data.put("categoryStats", categoryStats);
            data.put("trend7d", trend7d);

            result.put("code", 200);
            result.put("msg", "查询成功");
            result.put("data", data);
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
