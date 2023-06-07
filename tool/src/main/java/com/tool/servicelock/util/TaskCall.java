package com.tool.servicelock.util;

/**
 * @program: distribute-cache
 * @description: 有返回值的任务
 * @author: k
 * @create: 2022-05-28
 **/
@FunctionalInterface
public interface TaskCall<V> {

    V call();
}
