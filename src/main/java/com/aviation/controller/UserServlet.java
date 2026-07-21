package com.aviation.controller;

import com.aviation.dao.UserDao;
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
import java.util.Map;

@WebServlet(name = "UserServlet", urlPatterns = "/user")
public class UserServlet extends HttpServlet {

    private ObjectMapper objectMapper = new ObjectMapper();
    private UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        HashMap<String, Object> result = new HashMap<>();
        String action = req.getParameter("action");

        try {
            if ("profile".equals(action)) {
                // 登录校验：只能查自己的信息
                User currentUser = AuthUtil.getLoginUser(req);
                if (currentUser == null) {
                    result.put("code", 401);
                    result.put("msg", "请先登录");
                    resp.getWriter().write(objectMapper.writeValueAsString(result));
                    return;
                }

                String idStr = req.getParameter("id");
                if (idStr == null || idStr.trim().isEmpty()) {
                    result.put("code", 400);
                    result.put("msg", "参数 id 不能为空");
                } else {
                    int id = Integer.parseInt(idStr);
                    // 只允许查自己的信息
                    if (id != currentUser.getId()) {
                        result.put("code", 403);
                        result.put("msg", "无权查看他人信息");
                    } else {
                        User user = userDao.findById(id);
                        result.put("code", 200);
                        result.put("msg", "查询成功");
                        result.put("data", user);
                    }
                }

            } else if ("register".equals(action) || "changepwd".equals(action)) {
                doPost(req, resp);
                return;

            } else {
                result.put("code", 400);
                result.put("msg", "未知操作");
            }
        } catch (NumberFormatException e) {
            result.put("code", 400);
            result.put("msg", "参数 id 格式错误");
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
            if ("register".equals(action)) {
                InputStream stream = req.getInputStream();
                User user = objectMapper.readValue(stream, User.class);

                if (user.getUsername() == null || user.getUsername().trim().isEmpty()
                        || user.getPassword() == null || user.getPassword().trim().isEmpty()) {
                    result.put("code", 400);
                    result.put("msg", "用户名和密码不能为空");
                } else if (userDao.findByUsername(user.getUsername()) != null) {
                    result.put("code", 400);
                    result.put("msg", "用户名已存在");
                } else {
                    // 强制忽略前端传的 role，注册一律为 operator，防止越权注册管理员
                    user.setRole("operator");
                    int rows = userDao.add(user);
                    if (rows > 0) {
                        result.put("code", 200);
                        result.put("msg", "注册成功");
                    } else {
                        result.put("code", 500);
                        result.put("msg", "注册失败");
                    }
                }

            } else if ("changepwd".equals(action)) {
                // 登录校验：只能改自己的密码
                User currentUser = AuthUtil.getLoginUser(req);
                if (currentUser == null) {
                    result.put("code", 401);
                    result.put("msg", "请先登录");
                    resp.getWriter().write(objectMapper.writeValueAsString(result));
                    return;
                }

                InputStream stream = req.getInputStream();
                Map<String, Object> body = objectMapper.readValue(stream, Map.class);
                Object idObj = body.get("id");
                if (idObj == null) {
                    result.put("code", 400);
                    result.put("msg", "参数 id 不能为空");
                } else {
                    int id = ((Number) idObj).intValue();
                    // 只允许改自己的密码
                    if (id != currentUser.getId()) {
                        result.put("code", 403);
                        result.put("msg", "无权修改他人密码");
                    } else {
                        String oldPassword = (String) body.get("oldPassword");
                        String newPassword = (String) body.get("newPassword");

                        User user = userDao.findById(id);
                        if (user == null) {
                            result.put("code", 400);
                            result.put("msg", "用户不存在");
                        } else if (oldPassword == null || !oldPassword.equals(user.getPassword())) {
                            result.put("code", 400);
                            result.put("msg", "原密码错误");
                        } else if (newPassword == null || newPassword.trim().isEmpty()) {
                            result.put("code", 400);
                            result.put("msg", "新密码不能为空");
                        } else {
                            int rows = userDao.updatePassword(id, newPassword);
                            if (rows > 0) {
                                result.put("code", 200);
                                result.put("msg", "密码修改成功");
                            } else {
                                result.put("code", 500);
                                result.put("msg", "密码修改失败");
                            }
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
