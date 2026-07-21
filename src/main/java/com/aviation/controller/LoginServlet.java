package com.aviation.controller;

import com.aviation.dao.UserDao;
import com.aviation.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

@WebServlet(name = "LoginServlet", urlPatterns = "/login")
public class LoginServlet extends HttpServlet {

    private ObjectMapper objectMapper = new ObjectMapper();
    private UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        HashMap<String, Object> result = new HashMap<>();

        try {
            InputStream stream = req.getInputStream();
            User loginUser = objectMapper.readValue(stream, User.class);

            System.out.println("=== 登录调试 ===");
            System.out.println("收到用户名: [" + loginUser.getUsername() + "]");
            System.out.println("收到密码: [" + loginUser.getPassword() + "]");
            System.out.println("===============");

            User user = userDao.login(loginUser.getUsername(), loginUser.getPassword());

            if (user != null) {
                req.getSession().setAttribute("user", user);
                result.put("code", 200);
                result.put("msg", "登录成功");
                result.put("data", user);
            } else {
                result.put("code", 401);
                result.put("msg", "用户名或密码错误");
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", "服务器内部错误");
        }

        resp.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
