package com.CloudStorage.service.impl;

import com.CloudStorage.entity.constants.Constants;
import com.CloudStorage.entity.dto.SessionShareDto;
import com.CloudStorage.entity.enums.PageSize;
import com.CloudStorage.entity.enums.ResponseCodeEnum;
import com.CloudStorage.entity.enums.ShareValidTypeEnums;
import com.CloudStorage.entity.po.FileShare;
import com.CloudStorage.entity.query.FileShareQuery;
import com.CloudStorage.entity.query.SimplePage;
import com.CloudStorage.entity.vo.PaginationResultVO;
import com.CloudStorage.exception.BusinessException;
import com.CloudStorage.mappers.FileShareMapper;
import com.CloudStorage.service.FileShareService;
import com.CloudStorage.utils.DateUtil;
import com.CloudStorage.utils.StringTools;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;


/**
 * 分享信息 业务接口实现
 */
@Service("fileShareService")
public class FileShareServiceImpl implements FileShareService {

    @Resource
    private FileShareMapper<FileShare, FileShareQuery> fileShareMapper;

    /**
     * 根据条件查询列表
     */
    @Override
    public List<FileShare> findListByParam(FileShareQuery param) {
        return this.fileShareMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(FileShareQuery param) {
        return this.fileShareMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<FileShare> findListByPage(FileShareQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<FileShare> list = this.findListByParam(param);
        PaginationResultVO<FileShare> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(FileShare bean) {
        return this.fileShareMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<FileShare> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.fileShareMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<FileShare> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.fileShareMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 根据ShareId获取对象
     */
    @Override
    public FileShare getFileShareByShareId(String shareId) {
        return this.fileShareMapper.selectByShareId(shareId);
    }

    /**
     * 根据ShareId修改
     */
    @Override
    public Integer updateFileShareByShareId(FileShare bean, String shareId) {
        return this.fileShareMapper.updateByShareId(bean, shareId);
    }

    /**
     * 根据ShareId删除
     */
    @Override
    public Integer deleteFileShareByShareId(String shareId) {
        return this.fileShareMapper.deleteByShareId(shareId);
    }

    @Override
    public void saveShare(FileShare share) {
        //1.获取分享类型
        ShareValidTypeEnums typeEnum = ShareValidTypeEnums.getByType(share.getValidType());
        //2.如果分享类型为空则报错
        if (null == typeEnum) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        //3.判断分享是否永久有效
        if (typeEnum != ShareValidTypeEnums.FOREVER) {
            //3.1如果不是永久有效则取出有效期存储到分享信息类中
            share.setExpireTime(DateUtil.getAfterDate(typeEnum.getDays()));
        }
        Date curDate = new Date();
        //4.设置分享时间
        share.setShareTime(curDate);
        //5.判断提取码是否为空
        if (StringTools.isEmpty(share.getCode())) {
            //5.1如果不为空则设置提取码
            share.setCode(StringTools.getRandomString(Constants.LENGTH_5));
        }
        share.setShareId(StringTools.getRandomString(Constants.LENGTH_20));
        this.fileShareMapper.insert(share);
    }

    /**
     * 删除分享
     * @param shareIdArray
     * @param userId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFileShareBatch(String[] shareIdArray, String userId) {
        Integer count = this.fileShareMapper.deleteFileShareBatch(shareIdArray, userId);
        //如果删除的分享数据异常则事务回滚
        if (count != shareIdArray.length) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
    }

    /**
     * 校验分享码
     * @param shareId
     * @param code
     * @return
     */
    @Override
    public SessionShareDto checkShareCode(String shareId, String code) {
        //1.查询校验码
        FileShare share = this.fileShareMapper.selectByShareId(shareId);
        //2.判断校验码是否正确
        if (null == share || (share.getExpireTime() != null && new Date().after(share.getExpireTime()))) {
            throw new BusinessException(ResponseCodeEnum.CODE_902);
        }
        if (!share.getCode().equals(code)) {
            throw new BusinessException("提取码错误");
        }
        //3.更新浏览次数
        this.fileShareMapper.updateShareShowCount(shareId);
        SessionShareDto shareSessionDto = new SessionShareDto();
        shareSessionDto.setShareId(shareId);
        shareSessionDto.setShareUserId(share.getUserId());
        shareSessionDto.setFileId(share.getFileId());
        shareSessionDto.setExpireTime(share.getExpireTime());
        return shareSessionDto;
    }
}