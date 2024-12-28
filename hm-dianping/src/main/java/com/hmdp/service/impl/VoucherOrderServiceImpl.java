package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService voucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    private IVoucherOrderService proxy;

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
//                    VoucherOrder voucherOrder = orderTasks.take();
                    // 获取消息队列中的订单信息 xreadgroup group g1 c1 count 1 block 2000 streams stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );

                    // 判断消息是否获取成功
                    // 如果获取失败 说明没有消息 继续下次循环
                    if (list == null || list.isEmpty()) continue;

                    // 获取成功
                    // 解析消息中的订单信息
                    MapRecord<String, Object, Object> mapRecord = list.get(0);
                    Map<Object, Object> value = mapRecord.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);
                    // ACK 确认
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", mapRecord.getId());

                } catch (Exception e) {
                    handlePendingList();
                    log.info("处理订单异常", e);
                }
            }
        }
    }

    private void handlePendingList() {
        while (true) {
            try {
                // 获取pendinglist中的订单信息 xreadgroup group g1 c1 count 1 block 2000 streams stream.orders 0
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create("stream.orders", ReadOffset.from("0"))
                );

                // 判断消息是否获取成功
                // 如果获取失败 说明没有异常消息 结束循环
                if (list == null || list.isEmpty()) break;

                // 获取成功
                // 解析消息中的订单信息
                MapRecord<String, Object, Object> mapRecord = list.get(0);
                Map<Object, Object> value = mapRecord.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                handleVoucherOrder(voucherOrder);
                // ACK 确认
                stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", mapRecord.getId());

            } catch (Exception e) {
                log.info("处理订单异常", e);
            }

        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 获取用户
        Long userId = voucherOrder.getUserId();
        // 创建锁对象
        RLock simpleRedisLock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = simpleRedisLock.tryLock();
        if (!isLock) {
            log.error("不允许重复下单！");
            return;
        }

        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            simpleRedisLock.unlock();
        }
    }

    /**
     * 初版秒杀 低效
     * @param voucherId
     * @return
     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//
//        // 1、查询优惠券
//        SeckillVoucher voucher = voucherService.getById(voucherId);
//
//        // 2、判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            //尚未开始
//            return Result.fail("秒杀尚未开始！");
//        }
//
//        // 3、判断秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            // 已经结束
//            return Result.fail("秒杀已经结束！");
//        }
//
//        // 4、判断库存是否充足
//        if (voucher.getStock() < 1) {
//            // 库存不足
//            return Result.fail("库存不足！");
//        }
//
//        Long userId = UserHolder.getUser().getId();
//
//        // 创建锁对象
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock simpleRedisLock = redissonClient.getLock("lock:order:" + userId);
//
//
//        // 获取锁
//        boolean isLock = simpleRedisLock.tryLock();
//        if (!isLock) {
//            return Result.fail("不允许重复下单！");
//        }
//
//        try {
//            // 获取代理对象 （事务）
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            simpleRedisLock.unlock();
//        }
//    }

    /**
     * 使用 redis 优化版本（未使用队列）
     * @param voucherId
     * @return
     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        // 1、执行lua脚本
//        // 调用lua脚本
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(), userId.toString()
//        );
//        int r = result.intValue();
//        // 2、判断执行结果
//        // 2.1、不为0 没有购买资格
//        if (r != 0) {
//            return Result.fail(r == 1 ? "库存不足！" : "不能重复下单！");
//        }
//
//        // 2.2、拥有购买资格 把下单信息保存到阻塞队列
//        Long orderId = redisIdWorker.nextId("order");
//        // 6、创建订单对象
//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(UserHolder.getUser().getId());
//        voucherOrder.setVoucherId(voucherId);
//        // TODO 保存阻塞队列
//        orderTasks.add(voucherOrder);
//
//        // 获取代理对象 （事务）
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        // 返回订单id
//        return Result.ok(orderId);
//    }

    /**
     * 使用Stream消息队列
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long orderId = redisIdWorker.nextId("order");
        Long userId = UserHolder.getUser().getId();
        // 1、执行lua脚本
        // 调用lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), orderId.toString()
        );
        int r = result.intValue();
        // 2、判断执行结果
        // 2.1、不为0 没有购买资格
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足！" : "不能重复下单！");
        }

        // 获取代理对象 （事务）
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 返回订单id
        return Result.ok(orderId);
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 一人一单
        Long userId = UserHolder.getUser().getId();

        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("不允许重复下单！");
        }

        // 5、扣减库存
        boolean success = voucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
//                .eq("stock", voucher.getStock())
                .gt("stock", 0)
                .update();

        if (!success) {
            return Result.fail("库存不足！");
        }

        // 6、创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1 订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 6.2 用户id
        voucherOrder.setUserId(UserHolder.getUser().getId());
        // 6.3 代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        // 7、返回订单id
        return Result.ok(orderId);
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 一人一单
        Long userId = UserHolder.getUser().getId();

        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("不能重复购买！");
            return;
        }

        // 5、扣减库存
        boolean success = voucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();

        if (!success) {
            log.error("库存不足 ！");
            return;
        }

        save(voucherOrder);
    }
}
