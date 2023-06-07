package com.example.service.impl;

import com.example.dto.PayDto;
import com.example.entity.Pay;
import com.example.mapper.PayMapper;
import com.example.service.IPayService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @program: toolkit
 * @description:
 * @author: k
 * @create: 2023-04-24
 **/
@Service
public class PayService implements IPayService {
    
    @Autowired
    private PayMapper payMapper;
    @Override
    public Pay getById(Long id) {
        return payMapper.getById(id);
    }
    
    @Override
    public List<Pay> select(PayDto payDto) {
        return payMapper.select(payDto);
    }
    
    @Override
    public Integer insert(PayDto payDto) {
        Pay pay = new Pay();
        BeanUtils.copyProperties(payDto,pay);
        return payMapper.insert(pay);
    }
    
    @Override
    public Integer updateById(final Pay record) {
        return payMapper.updateById(record);
    }
}
