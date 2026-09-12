package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RedisLock {
    private  String name;
    private  StringRedisTemplate stringRedisTemplate;
    private  String id =Long.toString(Thread.currentThread().threadId())+ UUID.randomUUID().toString();
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT=new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unLock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public RedisLock(String name,StringRedisTemplate stringRedisTemplate){
        this.name=name;
        this.stringRedisTemplate=stringRedisTemplate;
    }

    public boolean trylock(long second){

        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent("lock:" + name, id, second, TimeUnit.SECONDS);


        return BooleanUtil.isTrue(b);
    }

    public void unlock(){
        stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of("lock:"+name),id);
        /*String s = stringRedisTemplate.opsForValue().get("lock:" + name);
        if(s.equals(id)) {
            Boolean delete = stringRedisTemplate.delete("lock:" + name);
        }*/
    }

}
