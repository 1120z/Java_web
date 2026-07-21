package com.aviation.controller;

import com.aviation.dao.InventoryRecordDao;
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
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

@WebServlet(name = "RecordServlet", urlPatterns = "/record")
public class RecordServlet extends HttpServlet {

    private ObjectMapper objectMapper = new ObjectMapper();
    private InventoryRecordDao inventoryRecordDao = new InventoryRecordDao();

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
            User currentUser = AuthUtil.getLoginUser(req);
            if (currentUser == null) {
                result.put("code", 401);
                result.put("msg", "请先登录");
                resp.getWriter().write(objectMapper.writeValueAsString(result));
                return;
            }

            if ("list".equals(action)) {
                List<InventoryRecord> list = inventoryRecordDao.findAll();
                result.put("code", 200);
                result.put("msg", "查询成功");
                result.put("data", list);

            } else if ("bycomponent".equals(action)) {
                String componentIdStr = req.getParameter("componentId");
                if (componentIdStr == null || componentIdStr.trim().isEmpty()) {
                    result.put("code", 400);
                    result.put("msg", "参数 componentId 不能为空");
                } else {
                    List<InventoryRecord> list = inventoryRecordDao.findByComponentId(Integer.parseInt(componentIdStr));
                    result.put("code", 200);
                    result.put("msg", "查询成功");
                    result.put("data", list);
                }

            } else if ("search".equals(action)) {
                String componentIdStr = req.getParameter("componentId");
                String type = req.getParameter("type");
                String startDate = req.getParameter("startDate");
                String endDate = req.getParameter("endDate");
                Integer componentId = (componentIdStr != null && !componentIdStr.isEmpty()) ? Integer.parseInt(componentIdStr) : null;
                List<InventoryRecord> list = inventoryRecordDao.search(componentId, type, startDate, endDate);
                result.put("code", 200);
                result.put("msg", "查询成功");
                result.put("data", list);

            } else if ("add".equals(action)) {
                InputStream stream = req.getInputStream();
                InventoryRecord record = objectMapper.readValue(stream, InventoryRecord.class);

                // 参数校验
                if (record.getComponentId() == null || record.getComponentId() <= 0) {
                    result.put("code", 400);
                    result.put("msg", "参数 componentId 必须是正整数");
                } else if (!"in".equals(record.getType()) && !"out".equals(record.getType())) {
                    result.put("code", 400);
                    result.put("msg", "type 必须是 in 或 out");
                } else if (record.getQuantity() == null || record.getQuantity() <= 0) {
                    result.put("code", 400);
                    result.put("msg", "quantity 必须是正整数");
                } else {
                    // 操作人使用当前登录用户；库存与流水由 DAO 在同一事务中处理
                    record.setOperator(currentUser.getUsername());
                    InventoryRecordDao.OperationResult operationResult =
                            inventoryRecordDao.addAndUpdateStock(record);

                    if (operationResult.getStatus() == InventoryRecordDao.OperationStatus.SUCCESS) {
                        result.put("code", 200);
                        result.put("msg", "添加成功");
                        result.put("stockQuantity", operationResult.getStockQuantity());
                    } else if (operationResult.getStatus() == InventoryRecordDao.OperationStatus.INSUFFICIENT_STOCK) {
                        result.put("code", 400);
                        result.put("msg", "库存不足，当前库存：" + operationResult.getStockQuantity()
                                + "，出库数量：" + record.getQuantity());
                    } else if (operationResult.getStatus() == InventoryRecordDao.OperationStatus.COMPONENT_NOT_FOUND
                            || operationResult.getStatus() == InventoryRecordDao.OperationStatus.INVALID_REQUEST) {
                        result.put("code", 400);
                        result.put("msg", operationResult.getMessage());
                    } else {
                        result.put("code", 500);
                        result.put("msg", operationResult.getMessage());
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
