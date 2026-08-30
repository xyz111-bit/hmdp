package com.hmdp.utils;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginIntercepter implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public LoginIntercepter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
     /*   //对比session,如果不对，拦截
        HttpSession session=request.getSession();

        User user = (User)session.getAttribute("user");
        if(user==null){
            response.setStatus(401);
            return false;
        }
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        UserHolder.saveUser(userDTO);

        return true;*/
        String token=request.getHeader("authorization");

        if(StrUtil.isBlank(token)){
            response.setStatus(401);
            return false;
        }

        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(token);

        if(entries==null){
            response.setStatus(401);
            return false;
        }

        UserDTO userDTO=BeanUtil.fillBeanWithMap(entries,new UserDTO(), false);

        UserHolder.saveUser(userDTO);

        stringRedisTemplate.expire(token,RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
