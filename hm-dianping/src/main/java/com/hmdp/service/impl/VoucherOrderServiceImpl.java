package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    @Lazy
    private IVoucherOrderService voucherOrderService;

    @Override
    public Result seckillVoucher(Long voucherId) {
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
        synchronized (userId.toString().intern()) {
            //返回订单id
            return voucherOrderService.createVoucherOrder(userId,voucherId);

        }
    }

    @Transactional
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
    }



}
