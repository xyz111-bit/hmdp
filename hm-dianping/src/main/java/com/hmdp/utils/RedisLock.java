package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class RedisLock {
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    public RedisLock(String name,StringRedisTemplate stringRedisTemplate){
        this.name=name;
        this.stringRedisTemplate=stringRedisTemplate;
    }

    public boolean trylock(long second){
        long id = Thread.currentThread().threadId();
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent("lock:" + name, id+"", second, TimeUnit.SECONDS);


        return BooleanUtil.isTrue(b);
    }

    public void unlock(){
        Boolean delete = stringRedisTemplate.delete("lock:" + name);
    }

}
