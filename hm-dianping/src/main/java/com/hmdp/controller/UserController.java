package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.req.UserReq;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Param;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.List;

import static com.hmdp.utils.UserHolder.getUser;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // TODO 发送短信验证码并保存验证码
        return userService.sendCode(phone,session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // TODO 实现登录功能
        return userService.login(loginForm,session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    @GetMapping("/me")
    public Result me(){
        // TODO 获取当前登录的用户并返回
        log.info("获取到用户: {}", UserHolder.getUser());
        return Result.ok(UserHolder.getUser());
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable Long id) {
        User user = userService.getById(id);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    // 练习MP
    // 插入一条记录
    @PostMapping("/add")
    public Result addUser(@RequestBody UserReq req) {
        log.info(String.valueOf(req));
        Integer insert = userService.insert(req);
        if (null == insert) {
            return Result.fail("新增用户失败！");
        }
        return Result.ok();
    }
    // 删除记录
    @PostMapping("/remove")
    public Result removeUser(@RequestBody UserReq req) {
        Integer remove = userService.remove(req);
        if (null == remove) {
            return Result.fail("删除用户失败！");
        }
        return Result.ok();
    }

    // 更新记录
    @PostMapping("/update")
    public Result updateUser(@RequestBody UserReq req) {
        Integer update = userService.update(req);
        if (null == update) {
            return Result.fail("更新用户失败！");
        }
        return Result.ok();
    }

    // 查询多条记录
    @PostMapping("/list")
    public Result listUsers(@RequestBody UserReq req) {
        List<User> list = userService.list(req);
        if (null == list) {
            return Result.fail("查询用户失败！");
        }
        return Result.ok(list);
    }

    // 分页查询
    @PostMapping("/page")
    public Result pageUsers(@RequestBody UserReq req) {
        List<User> pageResult = userService.page(req);
        if (pageResult.isEmpty()) {
            return Result.fail("分页查询用户失败！");
        }
        return Result.ok(pageResult);
    }

    // Mapper接口
    // 插入数据
    @PostMapping("/insert")
    public Result insert(@RequestBody UserReq req) {
        Integer insert = userService.insertByMapper(req);
        if (null == insert) {
            return Result.fail("Mapper接口插入数据失败！");
        }
        return Result.ok();
    }
}
