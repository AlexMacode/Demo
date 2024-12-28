package com.hmdp.req;

import lombok.Data;

/**
 * Description: 用户请求类
 * Author: 马鹏丽
 * Date: 2024/12/14
 * Time: 20:35
 * Version: ${1.0}
 * Since: ${1.0}
 */
@Data
public class UserReq {
    /**
     * 手机号码
     */
    private String phone;

    /**
     * 密码，加密存储
     */
    private String password;

    /**
     * 昵称，默认是随机字符
     */
    private String nickName;

    /**
     * 用户头像
     */
    private String icon = "";

    private PageData pageData;
}