package com.example.service;

import com.example.redis.RedisCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * @program: cook-frame
 * @description:
 * @author: k
 * @create: 2024-01-22
 **/
@Slf4j
@Component
public class ProgramCacheOperateV2 {
    
    @Autowired
    private RedisCache redisCache;
    
    private DefaultRedisScript redisScript;
    
    @PostConstruct
    public void init(){
        try {
            redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/ProgramDataV2.lua")));
            redisScript.setResultType(String.class);
        } catch (Exception e) {
            log.error("redisScript init lua error",e);
        }
    }
    
    public String programCacheOperate(List<String> keys, String[] args){
        return (String)redisCache.getInstance().execute(redisScript, keys, args);
    }
}
