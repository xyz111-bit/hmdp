package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

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

        Boolean isLike = stringRedisTemplate.opsForSet().isMember(key, UserHolder.getUser().getId().toString());
        blog.setIsLike(isLike);

        return Result.ok(blog);

    }

    @Override
    public Result likeBlog(Long id) {
        String key="blog:like:"+id;
        Long userId = UserHolder.getUser().getId();
        //查询是否点过
        Boolean isLike = stringRedisTemplate.opsForSet().isMember(key, userId.toString());

        // 修改点赞数量
        //没点过
        if(BooleanUtil.isFalse(isLike)){
            boolean isTure = update()
                    .setSql("liked = liked + 1").eq("id", id).update();
            //成功添加
            if(isTure)
                stringRedisTemplate.opsForSet().add(key,userId.toString());
        }else if(isLike==null){
            boolean isTure = update()
                    .setSql("liked = liked + 1").eq("id", id).update();
            //成功添加
            if(isTure)
                stringRedisTemplate.opsForSet().add(key,userId.toString());
        } else {
            //点过
            boolean isTure = update()
                    .setSql("liked = liked - 1").eq("id", id).update();
            //成功，减去
            if(isTure)
                stringRedisTemplate.opsForSet().remove(key,userId.toString());
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
        // 查询用户
        records.forEach(blog ->{
            String key="blog:like:"+blog.getId();
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            Boolean isLike = stringRedisTemplate.opsForSet().isMember(key, UserHolder.getUser().getId().toString());
            blog.setIsLike(isLike);
        });
        return Result.ok(records);
    }
}
