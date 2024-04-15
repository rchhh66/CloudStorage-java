package com.CloudStorage.annotation;


import com.CloudStorage.entity.enums.VerifyRegexEnum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
//参数校验
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD})
public @interface VerifyParam {
    /**
     * 校验正则
     *
     * @return
     */
    VerifyRegexEnum regex() default VerifyRegexEnum.NO;

    /**
     * 最小长度
     *
     * @return
     */
    int min() default -1;

    /**
     * 最大长度
     *
     * @return
     */
    int max() default -1;

    /**
     * 是否必传
     * @return
     */
    boolean required() default false;
}
