package com.CloudStorage.controller;

import com.CloudStorage.annotation.GlobalInterceptor;
import com.CloudStorage.annotation.VerifyParam;
import com.CloudStorage.entity.dto.SessionWebUserDto;
import com.CloudStorage.entity.enums.FileDelFlagEnums;
import com.CloudStorage.entity.query.FileInfoQuery;
import com.CloudStorage.entity.vo.FileInfoVO;
import com.CloudStorage.entity.vo.PaginationResultVO;
import com.CloudStorage.entity.vo.ResponseVO;
import com.CloudStorage.service.FileInfoService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

@RestController("recycleController")
@RequestMapping("/recycle")
public class RecycleController extends ABaseController {

    @Resource
    private FileInfoService fileInfoService;

    /**
     * 根据条件分页查询
     */
    @RequestMapping("/loadRecycleList")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO loadRecycleList(HttpSession session, Integer pageNo, Integer pageSize) {
        FileInfoQuery query = new FileInfoQuery();
        query.setPageSize(pageSize);
        query.setPageNo(pageNo);
        query.setUserId(getUserInfoFromSession(session).getUserId());
        query.setOrderBy("recovery_time desc");
        query.setDelFlag(FileDelFlagEnums.RECYCLE.getFlag());
        PaginationResultVO result = fileInfoService.findListByPage(query);
        return getSuccessResponseVO(convert2PaginationVO(result, FileInfoVO.class));
    }

    /**
     * 恢复文件
     * @param session
     * @param fileIds
     * @return
     */
    @RequestMapping("/recoverFile")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO recoverFile(HttpSession session, @VerifyParam(required = true) String fileIds) {
        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        fileInfoService.recoverFileBatch(webUserDto.getUserId(), fileIds);
        return getSuccessResponseVO(null);
    }

    /**
     * 彻底删除文件
     * @param session
     * @param fileIds
     * @return
     */
    @RequestMapping("/delFile")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO delFile(HttpSession session, @VerifyParam(required = true) String fileIds) {
        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        fileInfoService.delFileBatch(webUserDto.getUserId(), fileIds, false);
        return getSuccessResponseVO(null);
    }
}
