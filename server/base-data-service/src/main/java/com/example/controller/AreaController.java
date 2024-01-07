package com.example.controller;

import com.example.common.ApiResponse;
import com.example.service.AreaService;
import com.example.vo.AreaVo;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author k
 * @since 2024-01-07
 */
@Controller
@RequestMapping("/area")
public class AreaController {
    
    @Autowired
    private AreaService areaService;
    
    @ApiOperation(value = "查询市区以及直辖市数据")
    @PostMapping(value = "/selectCityData")
    public ApiResponse<List<AreaVo>> selectCityData() {
        return ApiResponse.ok(areaService.selectCityData());
    }

}
