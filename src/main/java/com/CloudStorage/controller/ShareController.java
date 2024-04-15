package com.CloudStorage.controller;

import com.CloudStorage.annotation.GlobalInterceptor;
import com.CloudStorage.annotation.VerifyParam;
import com.CloudStorage.entity.dto.SessionWebUserDto;
import com.CloudStorage.entity.po.FileShare;
import com.CloudStorage.entity.query.FileShareQuery;
import com.CloudStorage.entity.vo.PaginationResultVO;
import com.CloudStorage.entity.vo.ResponseVO;
import com.CloudStorage.service.FileShareService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

@RestController("shareController")
@RequestMapping("/share")
public class ShareController extends ABaseController {
    @Resource
    private FileShareService fileShareService;

    /**
     * 获取分享列表
     * @param session
     * @param query
     * @return
     */
    @RequestMapping("/loadShareList")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO loadShareList(HttpSession session, FileShareQuery query) {
        query.setOrderBy("share_time desc");
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        query.setUserId(userDto.getUserId());
        query.setQueryFileName(true);
        PaginationResultVO resultVO = this.fileShareService.findListByPage(query);
        return getSuccessResponseVO(resultVO);
    }
    /**
     * 增加分享
     * @param session
     * @param fileId
     * @param validType
     * @param code
     * @return
     */
    @RequestMapping("/shareFile")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO shareFile(HttpSession session,
                                @VerifyParam(required = true) String fileId,
                                @VerifyParam(required = true) Integer validType,
                                String code) {
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        FileShare share = new FileShare();
        //创建文件分享类
        share.setFileId(fileId);
        share.setValidType(validType);
        share.setCode(code);
        share.setUserId(userDto.getUserId());
        fileShareService.saveShare(share);
        return getSuccessResponseVO(share);
    }

    /**
     * 取消分享
     * @param session
     * @param shareIds
     * @return
     */
    @RequestMapping("/cancelShare")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO cancelShare(HttpSession session, @VerifyParam(required = true) String shareIds) {
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        fileShareService.deleteFileShareBatch(shareIds.split(","), userDto.getUserId());
        return getSuccessResponseVO(null);
    }
}
