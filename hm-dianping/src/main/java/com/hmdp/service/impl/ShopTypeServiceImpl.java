package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

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
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {

        String typeListStr = stringRedisTemplate.opsForValue().get("cache:shop:typeList");
        if(StrUtil.isNotBlank(typeListStr)){
            return Result.ok(JSONUtil.toList(typeListStr,ShopType.class));
        }
        List<ShopType> typeList =query().orderByAsc("sort").list();
        if(CollectionUtils.isEmpty(typeList)){
            return Result.fail("没有商店类别数据");
        }
        String jsonStr = JSONUtil.toJsonStr(typeList);
        stringRedisTemplate.opsForValue().set("cache:shop:typeList",jsonStr);

        return Result.ok(typeList);

    }
}
