package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private RedisIdWorker redisIdWorker;

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

}
