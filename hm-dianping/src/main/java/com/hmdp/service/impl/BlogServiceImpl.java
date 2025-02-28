package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FEED_KEY;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;

    @Autowired
    private IFollowService followService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("用户不存在！");
        }
        queryUserByBlog(blog);
        if (UserHolder.getUser() != null) isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryUserByBlog(blog);
            if (UserHolder.getUser() != null) isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 点赞博客
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        String userId = UserHolder.getUser().getId().toString();
        Double isLike = stringRedisTemplate.opsForZSet().score(key, userId);
        if (Objects.isNull(isLike)) {
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId, System.currentTimeMillis());
            }
        } else {
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId);
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.EMPTY_LIST);
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> users = userService.query().in("id", ids)
                .last("order by FIELD(id," +  idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(users);
    }

    @Override
    public Result saveBlog(Blog blog) {
        UserDTO userDTO = UserHolder.getUser();
        blog.setUserId(userDTO.getId());
        boolean save = save(blog);
        // 推机制
        if (save) {
            // 查询作者的所有粉丝
            List<Follow> list = followService.query().eq("follow_user_id", userDTO.getId()).list();
            // 推送
            for (Follow follow : list) {
                // 获取粉丝id
                Long userId = follow.getUserId();
                // 推送
                String key = "feeds:" + userId;
                stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
            }
        }
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 查询收件箱
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        // 解析数据 blogId minTime offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            // 获取id
            String blogId = typedTuple.getValue();
            ids.add(Long.valueOf(blogId));
            long time = typedTuple.getScore().longValue();
            if (time == minTime) {
                os ++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        // 查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("order by field (id," + idStr + ")").list();

        for (Blog blog : blogs) {
            queryUserByBlog(blog);
            isBlogLiked(blog);
        }

        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(os);

        return Result.ok(scrollResult);
    }

    private void queryUserByBlog(Blog blog) {
        User user = userService.getById(blog.getUserId());
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void isBlogLiked(Blog blog) {
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double isMember = stringRedisTemplate.opsForZSet().score(key, UserHolder.getUser().getId().toString());
        blog.setIsLike(!Objects.isNull(isMember));
    }
}
