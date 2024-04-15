package com.CloudStorage.service.impl;

import com.CloudStorage.component.RedisComponent;
import com.CloudStorage.entity.config.AppConfig;
import com.CloudStorage.entity.constants.Constants;
import com.CloudStorage.entity.dto.SysSettingsDto;
import com.CloudStorage.entity.enums.PageSize;
import com.CloudStorage.entity.po.EmailCode;
import com.CloudStorage.entity.po.UserInfo;
import com.CloudStorage.entity.query.EmailCodeQuery;
import com.CloudStorage.entity.query.SimplePage;
import com.CloudStorage.entity.query.UserInfoQuery;
import com.CloudStorage.entity.vo.PaginationResultVO;
import com.CloudStorage.exception.BusinessException;
import com.CloudStorage.mappers.EmailCodeMapper;
import com.CloudStorage.mappers.UserInfoMapper;
import com.CloudStorage.service.EmailCodeService;
import com.CloudStorage.utils.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.List;


/**
 * 邮箱验证码 业务接口实现
 */
@Service("emailCodeService")
public class EmailCodeServiceImpl implements EmailCodeService {

    private static final Logger logger = LoggerFactory.getLogger(EmailCodeServiceImpl.class);

    @Resource
    private EmailCodeMapper<EmailCode, EmailCodeQuery> emailCodeMapper;

    @Resource
    private JavaMailSender javaMailSender;

    @Resource
    private AppConfig appConfig;

    @Resource
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    @Resource
    private RedisComponent redisComponent;

    /**
     * 根据条件查询列表
     */
    @Override
    public List<EmailCode> findListByParam(EmailCodeQuery param) {
        return this.emailCodeMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(EmailCodeQuery param) {
        return this.emailCodeMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<EmailCode> findListByPage(EmailCodeQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<EmailCode> list = this.findListByParam(param);
        PaginationResultVO<EmailCode> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(EmailCode bean) {
        return this.emailCodeMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<EmailCode> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.emailCodeMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<EmailCode> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.emailCodeMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 根据EmailAndCode获取对象
     */
    @Override
    public EmailCode getEmailCodeByEmailAndCode(String email, String code) {
        return this.emailCodeMapper.selectByEmailAndCode(email, code);
    }

    /**
     * 根据EmailAndCode修改
     */
    @Override
    public Integer updateEmailCodeByEmailAndCode(EmailCode bean, String email, String code) {
        return this.emailCodeMapper.updateByEmailAndCode(bean, email, code);
    }

    /**
     * 根据EmailAndCode删除
     */
    @Override
    public Integer deleteEmailCodeByEmailAndCode(String email, String code) {
        return this.emailCodeMapper.deleteByEmailAndCode(email, code);
    }

    /**
     * 邮件发送
     * @param Email
     * @param code
     */
    private void sendEmailCode(String Email, String code) {
        try {

            //1.创建MimeMessage对象
            MimeMessage message = javaMailSender.createMimeMessage();
            //
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            //2设置邮件的各种属性，包括发件人、收件人、主题和正文内容。
            //2.1设置邮件发件人
            helper.setFrom(appConfig.getSendUserName());
            //2.2邮件收件人 1或多个
            helper.setTo(Email);
            SysSettingsDto sysSettingsDto = redisComponent.getSysSettingsDto();
            //2.3邮件主题
            helper.setSubject(sysSettingsDto.getRegisterEmailTitle());
            //2.4邮件内容
            helper.setText(String.format(sysSettingsDto.getRegisterEmailContent(), code));
            //2.5邮件发送时间
            helper.setSentDate(new Date());
            javaMailSender.send(message);
        } catch (Exception e) {
            logger.error("邮件发送失败", e);
            throw new BusinessException("邮件发送失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendEmailCode(String Email, Integer type) {
        //1.如果type是注册，校验邮箱是否已存在
        if (type == Constants.ZERO) {
            //查询邮箱是否存在
            UserInfo userInfo = userInfoMapper.selectByEmail(Email);
            if (null != userInfo) {
                throw new BusinessException("邮箱已经存在");
            }
        }
        //2.生成qq邮箱的的验证码
        String code = StringTools.getRandomNumber(Constants.LENGTH_5);
        //3.调用发送邮件的方法
        sendEmailCode(Email, code);
        //4.使邮箱之前的验证码失效
        emailCodeMapper.disableEmailCode(Email);
        //5.在数据库中增加该邮箱并更新邮箱使用状态
        EmailCode emailCode = new EmailCode();
        emailCode.setCode(code);
        emailCode.setEmail(Email);
        emailCode.setStatus(Constants.ZERO);
        emailCode.setCreateTime(new Date());
        //4.将邮箱信息存入数据库
        emailCodeMapper.insert(emailCode);
    }

    @Override
    public void checkCode(String email, String code) {
        EmailCode emailCode = emailCodeMapper.selectByEmailAndCode(email, code);
        if (null == emailCode) {
            throw new BusinessException("邮箱验证码不正确");
        }
        if (emailCode.getStatus() == 1 || System.currentTimeMillis() - emailCode.getCreateTime().getTime() > Constants.LENGTH_15 * 1000 * 60) {
            throw new BusinessException("邮箱验证码已失效");
        }
        emailCodeMapper.disableEmailCode(email);
    }
}