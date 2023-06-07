package com.tool.servicelock.info;


/**
 * @program: distribute-cache
 * @description: 分布式锁失败处理接口实现类
 * @author: k
 * @create: 2022-05-28
 **/
public enum LockTimeOutStrategy implements LockTimeOutHandler{

    FAIL(){
        @Override
        public void handler(String lockName) {
            String msg = String.format("请求频繁",lockName);
            throw new RuntimeException(msg);
        }
    }
}
