package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result follow(Long id, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key="follow:"+userId;
        //判断
        if(BooleanUtil.isTrue(isFollow)){
            //如果是，增加
            Follow follow=new Follow();
            follow.setFollowUserId(id);
            follow.setUserId(userId);
            follow.setCreateTime(LocalDateTime.now());
            boolean isSuccess = save(follow);
            if(isSuccess)
                stringRedisTemplate.opsForSet().add(key,id.toString());
        }else if(BooleanUtil.isFalse(isFollow)){
            //如果不是，减少
            Wrapper<Follow> wrapper=new QueryWrapper<Follow>().eq("follow_user_id",id)
                    .eq("user_id",userId);
            boolean isSuccess = remove(wrapper);
            if(isSuccess)
                stringRedisTemplate.opsForSet().remove(key,id.toString());
        }else {
            return Result.fail("没有判断标志");
        }

        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        Long count = query().eq("follow_user_id", id)
                .eq("user_id", userId).count();
        if(count.compareTo(0L)>0){
            return Result.ok(Boolean.TRUE);
        }

        return Result.ok(Boolean.FALSE);
    }

    @Override
    public Result common(Long id) {
        Long userId=UserHolder.getUser().getId();
        String key1="follow:"+userId;
        String key2="follow:"+id;
        Set<String> stringSet = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(stringSet==null){
            return Result.ok(List.of());
        }
        List<Long> commonList = stringSet
                .stream().map(Long::valueOf)
                .toList();

        return Result.ok(commonList);
    }
}
