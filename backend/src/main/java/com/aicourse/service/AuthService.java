package com.aicourse.service;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.ClientInfo;
import java.util.Map;

/**
 * 认证授权业务接口
 */
public interface AuthService {

    /**
     * 用户登录
     * @param account 账号(用户名/手机号/邮箱)
     * @param password 密码
     * @param rememberMe 是否记住我
     * @param clientInfo 客户端上下文（IP / User-Agent，由 Web 层解析）
     * @return 登录信息(令牌、用户信息等)
     */
    ApiResponse<Map<String, Object>> login(String account, String password, Boolean rememberMe, ClientInfo clientInfo);

    /**
     * 用户注册
     * @param email 邮箱
     * @param username 用户名
     * @param password 密码
     * @param fullName 真实姓名
     * @param role 角色
     * @param phone 手机号
     * @param code 验证码
     * @param clientInfo 客户端上下文（IP / User-Agent，由 Web 层解析）
     * @return 注册结果
     */
    ApiResponse<Map<String, Object>> register(String email, String username, String password, String fullName,
                                              String role, String phone, String code, ClientInfo clientInfo);

    /**
     * 刷新访问令牌
     * @param refreshToken 刷新令牌
     * @return 新令牌信息
     */
    ApiResponse<Map<String, Object>> refreshToken(String refreshToken);

    /**
     * 退出登录
     * @param userId 用户ID
     * @param clientInfo 客户端上下文（IP / User-Agent，由 Web 层解析）
     * @return 操作结果
     */
    ApiResponse<Void> logout(Long userId, ClientInfo clientInfo);

    /**
     * 通过手机号+验证码重置密码
     * @param phone 手机号
     * @param code 验证码
     * @param newPassword 新密码
     * @return 操作结果
     */
    ApiResponse<Void> resetPassword(String phone, String code, String newPassword);

    /**
     * 修改登录密码
     * @param userId 用户ID
     * @param oldPassword 原密码
     * @param newPassword 新密码
     * @return 操作结果
     */
    ApiResponse<Void> changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 获取当前登录用户信息
     * @param userId 用户ID
     * @return 用户信息
     */
    ApiResponse<Map<String, Object>> getCurrentUser(Long userId);

    /**
     * 发送邮箱验证码
     * @param email 邮箱地址
     * @param type 验证码类型
     * @param clientInfo 客户端上下文（IP / User-Agent，由 Web 层解析）
     * @return 操作结果
     */
    ApiResponse<Void> sendVerificationCode(String email, String type, ClientInfo clientInfo);

    /**
     * 通过邮箱+验证码重置密码
     * @param email 邮箱
     * @param code 验证码
     * @param newPassword 新密码
     * @return 操作结果
     */
    ApiResponse<Void> resetPasswordByEmail(String email, String code, String newPassword);

    /**
     * 校验邮箱验证码有效性（仅校验，不消费；供找回密码第二步「验证邮箱」使用）
     * @param email 邮箱
     * @param code 验证码
     * @return 操作结果
     */
    ApiResponse<Void> verifyCode(String email, String code);
}