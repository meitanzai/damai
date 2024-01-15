package com.example.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.client.UserClient;
import com.example.common.ApiResponse;
import com.example.dto.OrderCreateDto;
import com.example.dto.OrderTicketUserCreateDto;
import com.example.dto.ProgramOrderCreateDto;
import com.example.dto.SeatDto;
import com.example.dto.UserGetAndTicketUserListDto;
import com.example.entity.Program;
import com.example.entity.Seat;
import com.example.entity.TicketCategory;
import com.example.enums.BaseCode;
import com.example.enums.SellStatus;
import com.example.exception.CookFrameException;
import com.example.mapper.ProgramMapper;
import com.example.mapper.SeatMapper;
import com.example.mapper.TicketCategoryMapper;
import com.example.util.DateUtils;
import com.example.vo.TicketUserVo;
import com.example.vo.UserGetAndTicketUserListVo;
import com.example.vo.UserVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @program: cook-frame
 * @description:
 * @author: k
 * @create: 2024-01-11
 **/
@Slf4j
@Service
public class ProgramOrderService {
    
    @Autowired
    private ProgramMapper programMapper;
    
    @Autowired
    private SeatMapper seatMapper;
    
    @Autowired
    private TicketCategoryMapper ticketCategoryMapper;
    
    @Autowired
    private UserClient userClient;
    
    @Autowired
    private UidGenerator uidGenerator;
    
    public void create(final ProgramOrderCreateDto programOrderCreateDto) {
        //验证手动选择座位和自动分配座位的参数是否正确
        final List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
        if (CollectionUtil.isEmpty(seatDtoList)) {
            if (Objects.isNull(programOrderCreateDto.getTicketCategoryId())) {
                throw new CookFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST);
            }
            if (Objects.isNull(programOrderCreateDto.getTicketCount())) {
                throw new CookFrameException(BaseCode.TICKET_COUNT_NOT_EXIST);
            }
        }
        //查询要购买的节目
        Program program = programMapper.selectById(programOrderCreateDto.getProgramId());
        if (Objects.isNull(program)) {
            throw new CookFrameException(BaseCode.PROGRAM_NOT_EXIST);
        }
        //验证用户和购票人信息正确性
        UserVo userVo = new UserVo();
        List<TicketUserVo> ticketUserVoList = new ArrayList<>();
        UserGetAndTicketUserListDto userGetAndTicketUserListDto = new UserGetAndTicketUserListDto();
        userGetAndTicketUserListDto.setUserId(programOrderCreateDto.getUserId());
        userGetAndTicketUserListDto.setTicketUserIdList(programOrderCreateDto.getTicketUserIdList());
        ApiResponse<UserGetAndTicketUserListVo> userGetAndTicketUserApiResponse = 
                userClient.getUserAndTicketUserList(userGetAndTicketUserListDto);
        if (Objects.equals(userGetAndTicketUserApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            UserGetAndTicketUserListVo userAndTicketUserListVo = 
                    Optional.ofNullable(userGetAndTicketUserApiResponse.getData())
                            .orElseThrow(() -> new CookFrameException(BaseCode.RPC_RESULT_DATA_EMPTY));
            if (Objects.isNull(userAndTicketUserListVo.getUserVo())) {
                throw new CookFrameException(BaseCode.USER_EMPTY);
            }
            ticketUserVoList = 
                    Optional.ofNullable(userAndTicketUserListVo.getTicketUserVoList()).filter(list -> !list.isEmpty())
                            .orElseThrow(() -> new CookFrameException(BaseCode.TICKET_USER_EMPTY));
            
        }else {
            log.error("user client rpc getUserAndTicketUserList error response : {}", JSON.toJSONString(userGetAndTicketUserApiResponse));
            throw new CookFrameException(userGetAndTicketUserApiResponse);
        }
        
        
        //传入的座位总价格
        BigDecimal parameterOrderPrice = new BigDecimal("0");
        //库中的座位总价格
        BigDecimal databaseOrderPrice = new BigDecimal("0");
        
        List<Seat> seatList = new ArrayList<>();
        //入参座位存在
        if (CollectionUtil.isNotEmpty(seatDtoList)) {
            //余票数量检测
            LambdaQueryWrapper<TicketCategory> tcLambdaQueryWrapper = Wrappers.lambdaQuery(TicketCategory.class)
                    .in(TicketCategory::getId, seatDtoList.stream().map(SeatDto::getTicketCategoryId));
            List<TicketCategory> ticketCategories = ticketCategoryMapper.selectList(tcLambdaQueryWrapper);
            Map<Long, Long> seatTicketCategoryDtoCount = seatDtoList.stream()
                    .collect(Collectors.groupingBy(SeatDto::getTicketCategoryId, Collectors.counting()));
            for (TicketCategory ticketCategory : ticketCategories) {
                Long count = Optional.ofNullable(seatTicketCategoryDtoCount.get(ticketCategory.getId())).orElseThrow(() -> new CookFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2));
                Long remainNumber = ticketCategory.getRemainNumber();
                if (count > remainNumber) {
                    throw new CookFrameException(BaseCode.TICKET_REMAIN_NUMBER_NOT_SUFFICIENT);
                }
            }
            
            //构建批量座位查询条件
            LambdaQueryWrapper<Seat> seatLambdaQueryWrapper = Wrappers.lambdaQuery(Seat.class)
                    .eq(Seat::getProgramId,programOrderCreateDto.getProgramId())
                    .eq(Seat::getSellStatus, SellStatus.NO_SOLD.getCode());
            for (SeatDto seatDto : seatDtoList) {
                seatLambdaQueryWrapper.or(i -> i
                        .eq(Seat::getTicketCategoryId,seatDto.getTicketCategoryId())
                        .eq(Seat::getRowCode,seatDto.getRowCode())
                        .eq(Seat::getColCode,seatDto.getColCode()));
                seatList = seatMapper.selectList(seatLambdaQueryWrapper);
                
            }
            //循环入参的座位对象
            for (SeatDto seatDto : seatDtoList) {
                Map<String, Seat> seatMap = seatList.stream().collect(Collectors
                        .toMap(seat -> seat.getRowCode() + "-" + seat.getColCode(), seat -> seat, (v1, v2) -> v2));
                //验证入参的对象在库中的状态，不存在、已锁、已售卖
                Seat seat = seatMap.get(seatDto.getRowCode() + "-" + seatDto.getColCode());
                if (Objects.isNull(seat)) {
                    throw new CookFrameException(BaseCode.SEAT_NOT_EXIST);
                }
                if (Objects.equals(seat.getSellStatus(), SellStatus.LOCK.getCode())) {
                    throw new CookFrameException(BaseCode.SEAT_LOCK);
                }
                if (Objects.equals(seat.getSellStatus(), SellStatus.SOLD.getCode())) {
                    throw new CookFrameException(BaseCode.SEAT_SOLD);
                }
                //将入参的座位价格进行累加
                parameterOrderPrice = parameterOrderPrice.add(seatDto.getPrice());
                //将库中的座位价格进行类型
                databaseOrderPrice = databaseOrderPrice.add(seat.getPrice());
                if (parameterOrderPrice.compareTo(databaseOrderPrice) > 0) {
                    throw new CookFrameException(BaseCode.PRICE_ERROR);
                }
            }
            
            
            
        }else {
            //入参座位不存在，利用算法自动根据人数和票档进行分配相邻座位
            Long ticketCategoryId = programOrderCreateDto.getTicketCategoryId();
            Integer ticketCount = programOrderCreateDto.getTicketCount();
            //根据票档和购买数量，查询出此节目下并且是可以售卖的座位列表
            LambdaQueryWrapper<Seat> seatLambdaQueryWrapper = Wrappers.lambdaQuery(Seat.class)
                    .eq(Seat::getProgramId, programOrderCreateDto.getProgramId())
                    .eq(Seat::getTicketCategoryId, ticketCategoryId)
                    .eq(Seat::getSellStatus, SellStatus.NO_SOLD.getCode());
            List<Seat> seats = seatMapper.selectList(seatLambdaQueryWrapper);
            //算法根据购买数量进行匹配座位
            seatList = SeatMatch.findAdjacentSeats(seats, ticketCount);
            //如果匹配出来的座位数量小于要购买的数量，拒绝执行
            if (seatList.size() < ticketCount) {
                throw new CookFrameException(BaseCode.SEAT_OCCUPY);
            }
        }
        //构建锁座位条件
        Seat updateSeat = new Seat();
        updateSeat.setSellStatus(SellStatus.LOCK.getCode());
        LambdaUpdateWrapper<Seat> seatLambdaUpdateWrapper = Wrappers.lambdaUpdate(Seat.class)
                .eq(Seat::getSellStatus,SellStatus.NO_SOLD.getCode())
                .eq(Seat::getProgramId,programOrderCreateDto.getProgramId());
        
        //构建剩余锁座位的条件构建完毕
        for (final Seat seat : seatList) {
            seatLambdaUpdateWrapper.or(i ->i
                    .eq(Seat::getTicketCategoryId,seat.getTicketCategoryId())
                    .eq(Seat::getColCode,seat.getColCode())
                    .eq(Seat::getRowCode,seat.getRowCode()));
        }
        //锁座位
        seatMapper.update(updateSeat, seatLambdaUpdateWrapper);
        
        
        //扣票数
        Map<Long, Long> ticketCategoryCount = seatList.stream()
                .collect(Collectors.groupingBy(Seat::getTicketCategoryId, Collectors.counting()));
        for (Entry<Long, Long> entry : ticketCategoryCount.entrySet()) {
            Long ticketCategoryId = entry.getKey();
            Long count = entry.getValue();
            ticketCategoryMapper.updateRemainNumber(ticketCategoryId,count);
        }
        
        //主订单参数构建
        OrderCreateDto orderCreateDto = new OrderCreateDto();
        orderCreateDto.setId(uidGenerator.getUID());
        orderCreateDto.setProgramId(programOrderCreateDto.getProgramId());
        orderCreateDto.setUserId(programOrderCreateDto.getUserId());
        orderCreateDto.setOrderPrice(parameterOrderPrice);
        orderCreateDto.setCreateOrderTime(DateUtils.now());
        
        //购票人订单构建
        List<OrderTicketUserCreateDto> orderTicketUserCreateDtoList = new ArrayList<>();
        for (int i = 0; i < ticketUserVoList.size(); i++) {
            TicketUserVo ticketUserVo = ticketUserVoList.get(i);
            OrderTicketUserCreateDto orderTicketUserCreateDto = new OrderTicketUserCreateDto();
            orderTicketUserCreateDto.setOrderId(orderCreateDto.getId());
            orderTicketUserCreateDto.setProgramId(programOrderCreateDto.getProgramId());
            orderTicketUserCreateDto.setUserId(programOrderCreateDto.getUserId());
            orderTicketUserCreateDto.setTicketUserId(ticketUserVo.getId());
            Seat seat = Optional.ofNullable(seatList.get(i)).orElseThrow(() -> new CookFrameException(BaseCode.SEAT_NOT_EXIST));
            orderTicketUserCreateDto.setSeatId(seat.getId());
            orderTicketUserCreateDto.setOrderPrice(seat.getPrice());
            orderTicketUserCreateDto.setCreateOrderTime(DateUtils.now());
            orderTicketUserCreateDtoList.add(orderTicketUserCreateDto);
        }
        
        orderCreateDto.setOrderTicketUserCreateDtoList(orderTicketUserCreateDtoList);
        
    }
}
