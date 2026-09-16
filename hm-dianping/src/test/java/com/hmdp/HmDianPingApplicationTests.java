package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService es=Executors.newFixedThreadPool(500);

    @Test
    public void firstTest(){
        shopService.saveShop2Redis(1L,10L);
    }

    @Test
    public void seTest(){

        CountDownLatch latch=new CountDownLatch(300);
        long epochMilli1 = Instant.now().toEpochMilli();
        Runnable task=()->{
            for(int i=0;i<100;i++){
                long id = redisIdWorker.nextId("order");


                System.out.println("\nid="+Long.toBinaryString(id));
            }
            latch.countDown();
        };
        for (int i = 0; i <300 ; i++) {
            es.submit(task);

        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        long epochMilli2 = Instant.now().toEpochMilli();
        long l = epochMilli2 - epochMilli1;
        System.out.println("时间为"+l);


    }

    @Test
    public void webTest(){
        long id = redisIdWorker.nextId("orders");
        System.out.println(Long.toBinaryString(id));
    }

    @Test
    public void addShopGeo(){
        //查询商户
        List<Shop> shopList = shopService.list();
        //对于每个商户
        for (Shop shop : shopList) {
            //获得商户类型id，为key
            String key="shop:geo:"+shop.getTypeId();
            //向key中加入坐标
//            stringRedisTemplate.opsForGeo().add(key,,shop.getId().toString());
            stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(),shop.getY()),shop.getId().toString());
        }

    }

}
