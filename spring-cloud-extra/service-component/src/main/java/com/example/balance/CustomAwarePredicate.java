package com.example.balance;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.cloud.nacos.ribbon.NacosServer;
import com.example.enums.BaseCode;
import com.example.exception.ToolkitException;
import com.example.threadlocal.BaseParameterHolder;
import com.google.common.collect.Maps;
import com.netflix.loadbalancer.AbstractServerPredicate;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.PredicateKey;
import com.netflix.loadbalancer.Server;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

import static com.example.constant.Constant.MARK_FLAG_FALSE;
import static com.example.constant.Constant.MARK_FLAG_TRUE;
import static com.example.constant.Constant.MARK_PARAMETER;

/**
 * @program: 灰度版本选择负载均衡选择器
 * @description:
 * @author: 星哥
 * @create: 2023-04-17
 **/
@Slf4j
public class CustomAwarePredicate extends AbstractServerPredicate{
	
	
	private String mark;
	
	private CustomEnabledRule customEnabledRule;
	
	public CustomAwarePredicate(String mark, CustomEnabledRule customEnabledRule){
		super(customEnabledRule);
		this.mark = mark;
		this.customEnabledRule = customEnabledRule;
	}
	
	/**
	 * 灰度调用服务的说明：
	 * 
	 * 请求服务中请求头中的参数 mark=false:请求生产的服务。 mark=true:请求灰度的服务
	 * 
	 * 被调用服务的配置:
	 *   spring.cloud.nacos.discovery.metadata.mark=false:代表生产的服务
	 *   spring.cloud.nacos.discovery.metadata.mark=true:代表灰度的服务
	 *
	 * 如果请求服务中请求头没有mark参数，或者该参数中的值不是true或false字符串(不区分大小写)则认为mark=false
	 * 
	 * 如果被调用服务的 spring.cloud.nacos.discovery.metadata.mark 配置项没有配置，或者为空，或者该配置项中的值不是true或false字符串(不区分大小写)则认为mark=false
	 * 判断逻辑:
	 *   如果所有被调用服务端的配置项 --spring.cloud.nacos.discovery.metadata.mark=true，并且请求中的Header参数 mark=true，则在该请求的n次调用中apply()函数都返回true，走负载均衡
	 *   否则被调用服务端的配置项 --spring.cloud.nacos.discovery.metadata.mark 必须与请求中的Header参数 mark 值相等 apply()函数才会返回true
	 * 
	 * 总结:
	 *   生产的请求必须走生产的服务(没有部署生产服务就熔断)，灰度的请求在有灰度服务部署的情况下必须走灰度的，没有灰度服务的情况下则调用生产的服务
	 */
	@Override
    public boolean apply(PredicateKey input) {
		boolean result;
		try {
			RequestAttributes ra = RequestContextHolder.getRequestAttributes();
			String markFromRequest = null;
			if (ra != null) {
				ServletRequestAttributes sra = (ServletRequestAttributes) ra;
				HttpServletRequest request = sra.getRequest();
				markFromRequest = request.getHeader(MARK_PARAMETER);
			}else {
				markFromRequest = BaseParameterHolder.getParameter(MARK_PARAMETER);
			}
			if (StringUtils.isEmpty(markFromRequest)) {
				markFromRequest = mark;
			}
			NacosServer nacosServer = (NacosServer) input.getServer();
			Map<String, String> metadata = nacosServer.getInstance().getMetadata();
			String markFromMetaData;
			if (metadata != null || metadata.isEmpty() == true) {
				markFromMetaData = MARK_FLAG_FALSE;
			}else {
				markFromMetaData = metadata.get(MARK_PARAMETER);
			}
			//判断如果被调用端没有灰度配置则默认配置为生产环境
			if(markFromMetaData == null || !(markFromMetaData.equalsIgnoreCase(MARK_FLAG_FALSE) || markFromMetaData.equalsIgnoreCase(MARK_FLAG_TRUE))) {
				markFromMetaData = MARK_FLAG_FALSE;
			}
			//判断如果请求端没有灰度标识则默认配置为生产环境
			if(markFromRequest == null || !(markFromRequest.equalsIgnoreCase(MARK_FLAG_FALSE) || markFromRequest.equalsIgnoreCase(MARK_FLAG_FALSE))) {
				markFromRequest = MARK_FLAG_FALSE;
			}
			if(markFromMetaData.equalsIgnoreCase(markFromRequest)) {
				result = true;
			}else {
				result = false;
			}
			
			/*假如最后得到的结果为false，再做一次匹配
			 *
			 * 如果所有服务端的配置均为spring.cloud.nacos.discovery.metadata.mark=true,而调用请求端的请求头中的mark为true，则也允许结果返回true做负载均衡
			 *
			 * 反之如果所有服务端的配置为spring.cloud.nacos.discovery.metadata.mark=true,而调用请求端的请求头中的mark为false，则结果返回false,不允许做负载均衡
			 */
			if(result == false && markFromRequest.equalsIgnoreCase(MARK_FLAG_TRUE)) {
				if(customEnabledRule == null) {
					throw new ToolkitException(BaseCode.CUSTOM_ENABLED_RULE_EMPTY);
				}
				ILoadBalancer iLoadBalancer = customEnabledRule.getLoadBalancer();
				if(iLoadBalancer == null) {
					throw new ToolkitException(BaseCode.I_LOAD_BALANCER_RULE_EMPTY);
				}
				List<Server> serverList = iLoadBalancer.getReachableServers();
				if(CollUtil.isEmpty(serverList)) {
					throw new ToolkitException(BaseCode.SERVER_LIST_EMPTY);
				}
				Map<String,String> map = Maps.newHashMap();
				for (Server serverBalance : serverList) {
					NacosServer server = (NacosServer) serverBalance;
					String markFromBalance = server.getInstance().getMetadata().get(MARK_PARAMETER);
					//判断如果被调用端没有灰度配置则默认配置为生产环境
					if(markFromBalance == null || !(markFromBalance.equalsIgnoreCase(MARK_FLAG_FALSE) || markFromBalance.equalsIgnoreCase(MARK_FLAG_TRUE))) {
						markFromBalance = MARK_FLAG_FALSE;
					}
					map.put(markFromBalance,markFromBalance);
				}
				if(!map.containsKey(MARK_FLAG_TRUE)) {
					result = true;
				}
			}
		}catch (Exception e) {
			result = false;
			log.error("CustomAwarePredicate#apply error",e);
		}
		return result;
	}
}
