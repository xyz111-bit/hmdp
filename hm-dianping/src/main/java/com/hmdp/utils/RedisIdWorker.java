package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final long FIRSTTIME=1262304000L;

    public long nextId(String prefix){

//时间戳
        long second = Instant.now().getEpochSecond()-FIRSTTIME;
 //
        Long l = stringRedisTemplate.opsForValue().increment("IdGenerate:" +prefix+":"+ LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        long l1 = 0;
        try {
            l1 = l.longValue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return second<<32|l1;
    }

}
