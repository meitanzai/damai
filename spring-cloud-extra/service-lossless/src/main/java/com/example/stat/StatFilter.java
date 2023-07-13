package com.example.stat;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @program: toolkit
 * @description:
 * @author: k
 * @create: 2023-07-13
 **/
@Slf4j
@AllArgsConstructor
public class StatFilter extends OncePerRequestFilter {
    
    private final StatIndicator statIndicator;
    
    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain) throws ServletException, IOException {
        try {
            statIndicator.increment();
        }catch (Exception e) {
            log.error("prometheusIndicator.increment error",e);
        }
        filterChain.doFilter(request, response);
        //如果是按吞吐量的指标则在过滤器后进行统计
    }
}
