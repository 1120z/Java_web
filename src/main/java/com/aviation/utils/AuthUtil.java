package com.aviation.utils;

import com.aviation.entity.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * 鉴权工具：校验登录状态
 */
public class AuthUtil {

    /**
     * 检查是否已登录
     * @return 已登录的 User 对象，未登录返回 null
     */
    public static User getLoginUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) return null;
        Object user = session.getAttribute("user");
        return user instanceof User ? (User) user : null;
    }

    /**
     * 检查是否是管理员
     */
    public static boolean isAdmin(User user) {
        return user != null && "admin".equals(user.getRole());
    }
}
