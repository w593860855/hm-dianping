package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryShopById(Long id) {
        // 缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(
                CACHE_SHOP_KEY, id, Shop.class, this::getById,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 缓存击穿互斥锁
        // Shop shop = queryWithMutex(id);
        // // 缓存击穿逻辑过期
        // Shop shop = cacheClient.queryWithLogicalExpier(
        //         CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在!");
        }
        return Result.ok(shop);
    }

    //缓存穿透解决方案
    // public Shop queryWithPassThrough(Long id){
    //     String key = CACHE_SHOP_KEY + id;
    //     // 1. 从redis查询商铺缓存
    //     String shopJson = stringRedisTemplate.opsForValue().get(key);
    //     // 2. 判断是否存在
    //     if (StrUtil.isNotBlank(shopJson)) {
    //         // 3. 存在返回查询信息
    //         return JSONUtil.toBean(shopJson, Shop.class);
    //     }
    //     // 判断命中的是否为空字符串
    //     if (shopJson != null) {
    //         return null;
    //     }
    //
    //     // 4. 不存在，根据查询数据库
    //     Shop shop = getById(id);
    //     // 5. 不存在，返回404
    //     if (shop == null) {
    //         stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
    //         return null;
    //     }
    //     // 6. 存在写入redis
    //     stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
    //     // 7. 返回
    //     return shop;
    // }

    // 缓存击穿解决方案互斥锁
    // public Shop queryWithMutex(Long id){
    //     String key = CACHE_SHOP_KEY + id;
    //     // 1. 从redis查询商铺缓存
    //     String shopJson = stringRedisTemplate.opsForValue().get(key);
    //     // 2. 判断是否存在
    //     if (StrUtil.isNotBlank(shopJson)) {
    //         // 3. 存在返回查询信息
    //         return JSONUtil.toBean(shopJson, Shop.class);
    //     }
    //     // 判断命中的是否为空字符串
    //     if (shopJson != null) {
    //         return null;
    //     }
    //     // 4. 实现缓存重建
    //     // 4.1. 获取互斥锁
    //     String lockey = LOCK_SHOP_KEY + id;
    //     Shop shop = null;
    //     try {
    //         boolean isLock = tryLock(lockey);
    //         // 4.2. 判断是否成功
    //         if (!isLock) {
    //             // 4.3. 不成功休眠一段时间并重试
    //             Thread.sleep(50);
    //             return queryWithMutex(id);
    //         }
    //         // 4.4 成功，根据查询数据库
    //          shop = getById(id);
    //         // 模拟延时
    //         Thread.sleep(200);
    //
    //         // 5. 不存在，返回404
    //         if (shop == null) {
    //             stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
    //             return null;
    //         }
    //         // 6. 存在写入redis
    //         stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
    //
    //     } catch (InterruptedException e) {
    //         e.printStackTrace();
    //     }finally {
    //         // 7. 释放互斥锁
    //         unLock(lockey);
    //     }
    //     // 8. 返回
    //     return shop;
    // }

    // private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    //
    // // 逻辑过期解决缓存击穿
    // public Shop queryWithLogicalExpier(Long id){
    //     String key = CACHE_SHOP_KEY + id;
    //     // 1. 从redis查询商铺缓存
    //     String shopJson = stringRedisTemplate.opsForValue().get(key);
    //     // 2. 判断是否命中
    //     if (StrUtil.isBlank(shopJson)) {
    //         // 3. 不命中返回空
    //         return null;
    //     }
    //     // 4.命中先把json转成对象
    //     RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
    //     Shop shop = JSONUtil.toBean( (JSONObject) redisData.getData(), Shop.class);
    //     LocalDateTime expireTime = redisData.getExpireTime();
    //     // 5.判断缓存是否过期
    //     if(expireTime.isAfter(LocalDateTime.now())){
    //         // 5.1 未过期返回商铺信息
    //         return shop;
    //     }
    //
    //     // 5.2 过期获取互斥锁
    //     String lockKey = LOCK_SHOP_KEY + id;
    //     boolean isLock = tryLock(lockKey);
    //     // 6. 判断是否获取锁
    //     if (isLock){
    //         // 6.1 成功，开启独立线程，实现缓存重构
    //         CACHE_REBUILD_EXECUTOR.submit( () ->{
    //             try {
    //                 // 重建缓存
    //                 this.saveShopToRedis(id, 20L);
    //             } catch (Exception e) {
    //                throw new RuntimeException(e);
    //             }finally {
    //                 // 释放锁
    //                 unLock(lockKey);
    //             }
    //         });
    //     }
    //
    //     // 6.2 失败，返回店铺信息
    //     return shop;
    // }


    // 获取锁
    // private boolean tryLock(String key){
    //     Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    //     return BooleanUtil.isTrue(flag);
    // }

    // 删除锁
    // private void unLock(String key){
    //     stringRedisTemplate.delete(key);
    // }

    // 重建缓存
    // public void saveShopToRedis(Long id, Long expireSeconds) throws InterruptedException {
    //     // 1.查询店铺数据
    //     Shop shop = getById(id);
    //     // 模拟延迟
    //     Thread.sleep(200);
    //     // 2.封装逻辑过期时间
    //     RedisData redisData = new RedisData();
    //     redisData.setData(shop);
    //     redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
    //     // 3.写入redis
    //     stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    // }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        // 1. 更新数据库
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        updateById(shop);
        // 2. 删除缓存
        stringRedisTemplate.delete( CACHE_SHOP_KEY + id);
        return Result.ok();
    }


    public void saveShopToRedis(long l, long l1) {
    }
}
