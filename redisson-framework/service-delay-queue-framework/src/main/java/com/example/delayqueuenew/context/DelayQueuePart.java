package com.example.delayqueuenew.context;

import com.example.delayqueuenew.config.DelayQueueProperties;
import com.example.delayqueuenew.core.ConsumerTask;
import lombok.Data;
import org.redisson.api.RedissonClient;

/**
 * @program: cook-frame
 * @description: 
 * @author: k
 * @create: 2024-01-23
 **/
@Data
public class DelayQueuePart extends DelayQueueBasePart {
    
    /**
     * 客户端对象
     * */
    private final ConsumerTask consumerTask;
    
    public DelayQueuePart(RedissonClient redissonClient,DelayQueueProperties delayQueueProperties,ConsumerTask consumerTask){
        super(redissonClient,delayQueueProperties);
        this.consumerTask = consumerTask;
    }
}
