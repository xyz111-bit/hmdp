package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IFollowService followService;


    @Override
    public Result queryById(Integer id) {
        String key="blog:like:"+id;
        Blog blog = this.getById(id);
        if(blog==null){
            return Result.fail("笔记不存在");
        }
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());

        Double score = stringRedisTemplate.opsForZSet().score(key, UserHolder.getUser().getId().toString());
        if(score==null)
            blog.setIsLike(false);
        if (score!=null)
            blog.setIsLike(true);

        return Result.ok(blog);

    }

    @Override
    public Result likeBlog(Long id) {
        String key="blog:like:"+id;
        Long userId = UserHolder.getUser().getId();
        //查询是否点过
        Double isLike = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        double currentTimeMillis = (double)System.currentTimeMillis();
        // 修改点赞数量
        //没点过
        if(isLike==null){
            boolean isTure = update()
                    .setSql("liked = liked + 1").eq("id", id).update();
            //成功添加
            if(isTure)
           //     stringRedisTemplate.opsForSet().add(key,userId.toString());
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),currentTimeMillis);

        }
        /*else if(isLike==null){
            boolean isTure = update()
                    .setSql("liked = liked + 1").eq("id", id).update();
            //成功添加
            if(isTure)
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),currentTimeMillis);
            //    stringRedisTemplate.opsForSet().add(key,userId.toString());
        } */
        else {
            //点过
            boolean isTure = update()
                    .setSql("liked = liked - 1").eq("id", id).update();
            //成功，减去
            if(isTure)
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
        }


        return Result.ok();
    }

    @Override
    public Result queryHotBlog(Integer current) {

        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        UserDTO userDTO = UserHolder.getUser();
        // 查询用户
        records.forEach(blog ->{
            String key="blog:like:"+blog.getId();
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            Double score=null;
            String thisUserId;
            if(userDTO!=null) {
                thisUserId = userDTO.getId().toString();
                score = stringRedisTemplate.opsForZSet().score(key, thisUserId);
            }
            if(score==null)
                blog.setIsLike(false);
            if (score!=null)
                blog.setIsLike(true);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlogTop5(Long id) {
        String key="blog:like:"+id;
        Set<String> range = stringRedisTemplate.opsForZSet().range(key, 0, 5);
        if(range==null){
            return Result.ok();
        }
        List<UserDTO> userDTO = range.stream().map(e ->
                userService.getById(e)
        ).map(e ->
                BeanUtil.copyProperties(e, UserDTO.class)
        ).collect(Collectors.toList());
        Collections.reverse(userDTO);

        return Result.ok(userDTO);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if(!isSuccess)
            return Result.fail("保存博客失败");
        //查询粉丝
        List<Follow> followList = followService.query().eq("follow_user_id", user.getId()).list();
        //投到粉丝信箱里面
        double currentTimeMillis = System.currentTimeMillis();
        followList.stream().map(Follow::getUserId).forEach(e->{
            String key="feed:"+e;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),currentTimeMillis);
        });

        // 返回id
        return Result.ok(blog.getId());
    }
}
