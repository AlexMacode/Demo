package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Object queryTypeList() {

        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        // 1、先查缓存
        String shopType = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopType)) {
            // 2、存在 直接返回
            List<ShopType> typeList = JSONUtil.toList(shopType, ShopType.class);
            return typeList;
        }
        // 3、不存在 查数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();

        // 4、数据库也不存在
        if (typeList == null) {
            return null;
        }

        // 5、存在写入Redis缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));

        // 6、返回
        return typeList;
    }
}
