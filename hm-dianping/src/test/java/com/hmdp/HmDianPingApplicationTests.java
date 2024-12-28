package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Test
    void testSaveShop() {
        shopService.saveShop2Redis(1L, 10L);
    }

//    void testIdWorker() {
//        Runnable task = () -> {
//            for (int i = 0; i < 100; i ++) {
//                System.out.println(redisIdWorker.nextId("order"));
//            }
//        }
//    }

}
