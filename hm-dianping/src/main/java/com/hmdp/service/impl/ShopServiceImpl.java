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
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private ShopMapper shopMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    ExecutorService executor= Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
       // Shop shop = penetration(id);

        Shop shop=querryWithBreakdownLocal(id);
     /*   //从redis查询商品
        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);

        //如果查到返回
        if(StrUtil.isNotBlank(shopStr)){
            return Result.ok(BeanUtil.toBean(shopStr,Shop.class));
        }
        if(shopStr!=null){
            return Result.fail("查询失败");
        }

        //从数据库查询商品
        Shop shop = getById(id);
        //如果没有查到 返回错误
        if(shop==null){
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,"",3L,TimeUnit.MINUTES);
            return Result.fail("没有查询到商店");
        }

        //如果查到,写入redis
        String jsonStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY +id,jsonStr);
        stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_KEY +id,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回数据
*/
        if(shop==null){
            return Result.fail("没有查询到店铺");
        }
        return Result.ok(shop);
    }


    private Shop penetration(Long id){

        //从redis查询商品
        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);

        //如果查到返回
        if(StrUtil.isNotBlank(shopStr)){
            return JSONUtil.toBean(shopStr,Shop.class);
        }
        if(shopStr!=null){
            return null;
        }

        //从数据库查询商品
        Shop shop = getById(id);
        //如果没有查到 返回错误
        if(shop==null){
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,"",3L,TimeUnit.MINUTES);
            return null;
        }

        //如果查到,写入redis
        String jsonStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY +id,jsonStr);
        stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_KEY +id,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回数据
        return shop;


    }

    private Shop querryWithBreakdown(Long id){
        //从redis查询商品
        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);

        //如果查到返回
        if(StrUtil.isNotBlank(shopStr)){
            return JSONUtil.toBean(shopStr,Shop.class);
        }
        if(shopStr!=null){
            return null;
        }
        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
        boolean flag = trylock(lockKey);
        Shop shop = null;
        try {

            if(!flag){
                Thread.sleep(200);
                return querryWithBreakdown(id);
            }

            //从redis查询商品
            shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);

            if(StrUtil.isNotBlank(shopStr)){
                return JSONUtil.toBean(shopStr,Shop.class);
            }
            if(shopStr!=null){
                return null;
            }

            //从数据库查询商品
            Thread.sleep(300);
            shop = getById(id);
            //如果没有查到 返回错误
            if(shop==null){
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,"",3L,TimeUnit.MINUTES);
                return null;
            }

            //如果查到,写入redis
            String jsonStr = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY +id,jsonStr);
            stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_KEY +id,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            //返回数据
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if(flag)
                dellock(lockKey);
        }



        return shop;
    }

    private Shop querryWithBreakdownLocal(Long id){
        //从redis  查询
        //从redis查询商品
        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);
        //查不到，返回null
        if(StrUtil.isBlank(shopStr)){
            return null;
        }
        RedisData redisData= JSONUtil.toBean(shopStr, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        JSONObject data =(JSONObject)redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);

        //查到判断逻辑过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //没有过期 转换 返回数据

            return shop;
        }
        log.info("\n过期了");
        boolean flag=false;
        //过期，判断是否获取锁
        flag = trylock(RedisConstants.LOCK_SHOP_KEY+id);


            //获得 增加一个线程，
            //线程，查询数据库，重建缓存
            if(flag){
                //Double check
                String shopStrD = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);
                if(StrUtil.isBlank(shopStrD)){
                    dellock(RedisConstants.LOCK_SHOP_KEY+id);
                    return null;
                }
                RedisData redisDataD= JSONUtil.toBean(shopStrD, RedisData.class);
                LocalDateTime expireTimeD = redisDataD.getExpireTime();
                JSONObject dataD =(JSONObject)redisDataD.getData();
                Shop shopD = JSONUtil.toBean(dataD, Shop.class);
                if(expireTimeD.isAfter(LocalDateTime.now())){
                    dellock(RedisConstants.LOCK_SHOP_KEY+id);
                    return shopD;
                }
                executor.submit(()->{

                    try {
                        saveShop2Redis(id,10L);
                        log.info("\n线程重建数据");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        //释放锁
                        dellock(RedisConstants.LOCK_SHOP_KEY+id);
                    }
                });
            }




        //返回数据
        return shop;

    }


    private boolean trylock(String key){
        String value="1";
        long timeout=20;
        TimeUnit timeUnit=TimeUnit.SECONDS;
        Boolean ifAbsent = stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeout, timeUnit);

        return BooleanUtil.isTrue(ifAbsent);
    }

    private void dellock(String key){
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id,Long timeSeconds){
        Shop newShop = getById(id);
        RedisData redisData=new RedisData();
        redisData.setData(newShop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeSeconds));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }












}
