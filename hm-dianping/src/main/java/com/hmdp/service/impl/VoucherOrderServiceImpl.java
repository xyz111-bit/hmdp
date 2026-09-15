package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    @Lazy
    private IVoucherOrderService voucherOrderService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    private ExecutorService executorService= Executors.newFixedThreadPool(10);

    private Runnable runnable=()->{
        while(true){
            //TODO从中间件取出voucherOrder，需要阻塞取
            List<MapRecord<String, Object, Object>> read = stringRedisTemplate.opsForStream().read(
                    Consumer.from("g1", "c1"),
                    StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                    StreamOffset.create("stream.setKillOrder", ReadOffset.lastConsumed())
            );
            if(CollectionUtils.isEmpty(read)){
                try {
                    Thread.sleep(1000);
                    log.info("\n重试");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                continue;
            }
            MapRecord<String, Object, Object> mapRecord = read.get(0);
            Map<Object, Object> map= mapRecord.getValue();
            VoucherOrder voucherOrder = BeanUtil.toBean(map, VoucherOrder.class);
            try {
                voucherOrderService.createVoucherOrder(voucherOrder.getId(),voucherOrder.getUserId(),voucherOrder.getVoucherId());
                stringRedisTemplate.opsForStream().acknowledge("stream.setKillOrder","g1",mapRecord.getId());
            } catch (Exception e) {
                log.info("订单创建异常",e);
                while(true){
                    read = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create("stream.setKillOrder", ReadOffset.from("0"))
                    );
                    if(read==null){
                        break;
                    }
                    mapRecord = read.get(0);
                    map= mapRecord.getValue();
                    voucherOrder = BeanUtil.toBean(map, VoucherOrder.class);
                    try {
                        voucherOrderService.createVoucherOrder(voucherOrder.getId(),voucherOrder.getUserId(),voucherOrder.getVoucherId());
                        stringRedisTemplate.opsForStream().acknowledge("stream.setKillOrder","g1",mapRecord.getId());
                    }catch(Exception e1){
                        log.info("出现异常",e1);
                        continue;
                    }
                }
            }

        }
    };


    private static DefaultRedisScript<Long> redisScript;
    static {
        redisScript=new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("seckillVoucher.lua"));
        redisScript.setResultType(Long.class);
    }
    @PostConstruct
    public void init(){
        log.info("\n========== 初始化消费者线程 ==========");
        executorService.submit(runnable);
    }


    @Override
    public Result seckillVoucher(Long voucherId){
        //查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.query().eq("voucher_id", voucherId).one();
        //检查优惠券是否在时间内
        if(voucher.getBeginTime().isAfter(LocalDateTime.now()) || voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("不在优惠券的时间范围");
        }
        //检测剩余
        //检测一人一单
        //添加这个人的userId
        // 扣减库存
        Long userId = UserHolder.getUser().getId();
        long id = redisIdWorker.nextId("order");
        Long result = stringRedisTemplate.execute(redisScript, List.of(), voucherId.toString(), userId.toString(),Long.toString(id));
        //不成功
        if(!result.equals(0L)){
            String str;
            return Result.fail(str=result.equals(1L)?"没有库存了":"一人只能下一单");
        }
       /* //获取orderId，VoucherId,UserId
        VoucherOrder voucherOrder=new VoucherOrder();
        voucherOrder.setId(id);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);*/
        //TODO
        //向消息队列存voucherOrder

        
        return Result.ok(id);
    }
/*    @Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        //查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.query().eq("voucher_id", voucherId).one();
        //检查优惠券是否在时间内
        if(voucher.getBeginTime().isAfter(LocalDateTime.now()) || voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("不在优惠券的时间范围");
        }
        //检查优惠券是否有剩余
        if(voucher.getStock()<=0){
            return Result.fail("优惠券没有剩余");
        }
        Long userId = UserHolder.getUser().getId();
        RLock rLock = redissonClient.getLock("voucherOrderCreate:" + userId);
        //RedisLock redisLock=new RedisLock("voucherOrderCreate:"+userId,stringRedisTemplate);
        //synchronized (userId.toString().intern()) {
        boolean lock = rLock.tryLock(1, 100, TimeUnit.SECONDS);
        //boolean lock = redisLock.trylock(100L);
        if(!lock){
            return Result.fail("线程没有获取到锁");
        }
        try {
            //返回订单id
            return voucherOrderService.createVoucherOrder(userId, voucherId);
        }finally {
                rLock.unlock();
               // redisLock.unlock();

        }
        //}
    }*/

  /*  @Transactional
    public Result createVoucherOrder(Long userId,Long voucherId){

        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("已经下过此购物券");
        }
        //券数量减一
        boolean isUpdate = seckillVoucherService.update().setSql("stock=stock-1").eq("voucher_id", voucherId)
                .gt("stock", 0).update();
        if (!isUpdate) {
            return Result.fail("优惠券没有剩余");
        }
        //新增订单
        VoucherOrder voucherOrder = new VoucherOrder();

        voucherOrder.setId(redisIdWorker.nextId("order"));
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setCreateTime(LocalDateTime.now());
        voucherOrder.setUpdateTime(LocalDateTime.now());

        save(voucherOrder);

        return Result.ok(voucherOrder.getId());
    }*/
    @Transactional
    public void createVoucherOrder(Long id,Long userId,Long voucherId) {

        //券数量减一
        boolean isUpdate = seckillVoucherService.update().setSql("stock=stock-1").eq("voucher_id", voucherId)
                .gt("stock", 0).update();
        if (!isUpdate) {
            throw new RuntimeException("Error,缺少剩余");
        }
        //新增订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(id);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setCreateTime(LocalDateTime.now());
        voucherOrder.setUpdateTime(LocalDateTime.now());

        save(voucherOrder);
    }

}
