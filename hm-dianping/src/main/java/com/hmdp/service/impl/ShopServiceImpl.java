package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

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

    @Autowired
    private ShopMapper shopMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        //从redis查询商品
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




        return Result.ok(shop);


    }
}
