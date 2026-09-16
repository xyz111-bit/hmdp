package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.PasswordEncoder;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //检验手机号码
        boolean isPhoneInvalid = RegexUtils.isPhoneInvalid(phone);
        if(isPhoneInvalid==true){
            return Result.fail("电话格式错误");
        }

        //生成验证码
        String otp= RandomUtil.randomString(4);

//        //设置session
//        session.setAttribute("One-Time password",otp);
        //设置redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY +phone,otp,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //发送验证码
        log.info("\n验证码为{},{}",otp,otp.hashCode());

    return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        //校验手机号
        boolean isPhoneInvalid = RegexUtils.isPhoneInvalid(loginForm.getPhone());
        if(isPhoneInvalid==true){
            return Result.fail("电话格式错误");
        }
        //解析验证码
       /* Object otp = session.getAttribute("One-Time password");
        String otpUser=loginForm.getCode();
        if(session==null||!otpUser.equals(otp.toString())){
            return Result.fail("验证码输入错误");
        }*/
        String otp = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        String otpUser = loginForm.getCode();
        if(otp==null||!otpUser.equals(otp)){
            return Result.fail("验证码错误");
        }

        //查表，如果是新用户，新增用户
        User user = query().eq("phone", loginForm.getPhone()).one();

        if(user==null){
            user=new User();
            user.setPhone(loginForm.getPhone());
            user.setCreateTime(LocalDateTime.now());
            user.setCreateTime(LocalDateTime.now());
            user.setNickName("user_id"+RandomUtil.randomString(5));
            save(user);

        }
       /* //设置用户session
        session.setAttribute("user",user);
*/
        UUID uuid = UUID.randomUUID();
        String token=RedisConstants.LOGIN_USER_KEY+uuid;
        UserDTO userDTO=new UserDTO();
        BeanUtil.copyProperties(user,userDTO);
        Map<String,Object> map= new HashMap<>();
        map.put("icon",userDTO.getIcon());
        map.put("id",userDTO.getId().toString());
        map.put("nickName",userDTO.getNickName());

        stringRedisTemplate.opsForHash().putAll(token,map);
        stringRedisTemplate.expire(token,RedisConstants.LOGIN_USER_TTL,TimeUnit.SECONDS);

        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //获取用户Id
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        //获取日期
        LocalDate now = LocalDate.now();
        //得到key
        String sufix = now.format(DateTimeFormatter.ofPattern(":yyMM"));
        String key="user:sign:"+userId+sufix;
        //加入BitMap
        Boolean isSuccess = stringRedisTemplate.opsForValue().setBit(key, now.getDayOfMonth() - 1, true);
        return Result.ok();

    }

    @Override
    public Result signCount() {
        //获取用户Id
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        //获取日期
        LocalDate now = LocalDate.now();
        //得到key
        String sufix = now.format(DateTimeFormatter.ofPattern(":yyMM"));
        String key="user:sign:"+userId+sufix;
        int dayOfMonth = now.getDayOfMonth();
       // bitfield user:sign:2:2609 get u16 0
        List<Long> list = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if(list==null){
            return Result.ok(0);
        }
        Long l = list.get(0);
        if(l==null || l==0){
            return Result.ok(0);
        }
        int ll = l.intValue();
        int count=0;
        while (true){
            if((ll & 1)==1){
                count++;
            }else {
                break;
            }
            ll=ll>>>1;
        }

        return Result.ok(count);
    }
}
