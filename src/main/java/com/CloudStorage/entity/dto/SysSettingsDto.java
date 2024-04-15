package com.CloudStorage.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
/**
 * @JsonIgnoreProperties(ignoreUnknown = true)是一个Jackson注解，用于在将JSON数据映射到Java对象时忽略未知的属性。
 * 当使用Jackson库将JSON字符串反序列化为Java对象时，如果JSON中存在Java对象中没有定义的属性，通常会抛出异常。
 * 通过在类级别上添加@JsonIgnoreProperties(ignoreUnknown = true)注解，可以告诉Jackson忽略JSON中未知的属性，
 * 而不会抛出异常。
 */
//存储系统设置内容类
public class SysSettingsDto implements Serializable {
    /**
     * 注册发送邮件标题
     */
    private String registerEmailTitle = "邮箱验证码";

    /**
     * 注册发送邮件内容
     */
    private String registerEmailContent = "你好，您的邮箱验证码是：%s，15分钟有效";

    /**
     * 用户初始化空间大小 5M
     */
    private Integer userInitUseSpace = 5;

    public String getRegisterEmailTitle() {
        return registerEmailTitle;
    }

    public void setRegisterEmailTitle(String registerEmailTitle) {
        this.registerEmailTitle = registerEmailTitle;
    }

    public String getRegisterEmailContent() {
        return registerEmailContent;
    }

    public void setRegisterEmailContent(String registerEmailContent) {
        this.registerEmailContent = registerEmailContent;
    }

    public Integer getUserInitUseSpace() {
        return userInitUseSpace;
    }

    public void setUserInitUseSpace(Integer userInitUseSpace) {
        this.userInitUseSpace = userInitUseSpace;
    }
}
