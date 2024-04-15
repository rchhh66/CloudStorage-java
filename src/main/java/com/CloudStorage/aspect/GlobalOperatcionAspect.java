package com.CloudStorage.aspect;

import com.CloudStorage.annotation.GlobalInterceptor;
import com.CloudStorage.annotation.VerifyParam;
import com.CloudStorage.entity.constants.Constants;
import com.CloudStorage.entity.dto.SessionWebUserDto;
import com.CloudStorage.entity.enums.ResponseCodeEnum;
import com.CloudStorage.exception.BusinessException;
import com.CloudStorage.utils.StringTools;
import com.CloudStorage.utils.VerifyUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * 一.连接点：JoinPoint, 可以被AOP控制的方法（暗含方法执行时的相关信息）
 * 二.通知：Advice,指哪些重复逻辑，也就是共性功能（最终体现为一个方法）
 * 1.前置通知（Before Advice）：在目标方法执行之前执行的通知。
 * 2.后置通知（After Advice）：在目标方法执行之后执行的通知。
 * 3.返回通知（After Returning Advice）：在目标方法成功执行并返回结果后执行的通知。可以获取目标方法的返回值，并进行一些后续处理.
 * 4.异常通知（After Throwing Advice）：在目标方法抛出异常后执行的通知。可以捕获目标方法抛出的异常，并进行一些异常处理
 * 5.环绕通知（Around Advice）：在目标方法执行前后都执行的通知。可以完全控制目标方法的执行，包括是否执行目标方法、修改目标方法的参数、修改目标方法的
 * 返回值等。注意：@Around环绕通知需要自己调用 ProceedingJoinPoint.proceed() 来让原始方法执行，其他通知不需要考虑目标方法执行
 * @Around环绕通知方法的返回值，必须指定为Object，来接收原始方法的返回值。
 * 6.当有多个切面的切入点都匹配到了目标方法，目标方法运行时，多个通知方法都会被执行。此时通知顺序的规则为：①不同切面类中，默认按照切面类的类名字母排序：
 * 目标方法前的通知方法：字母排名靠前的先执行
 * 目标方法后的通知方法：字母排名靠前的后执行
 * ②@Order注解可以在切面类上或切面类中的通知方法上使用。它接受一个整数值作为参数，数值越小表示优先级越高，即先执行。默认情况下，
 * 切面的执行顺序是不确定的。
 * 三.切入点：PointCut,匹配连接点的条件，通知仅会在切入点方法执行时被应用
 *     切入点表达式：1.根据方法签名匹配execution()   2.根据注解匹配annotation(注解的全类名)
 *四.切面：Aspect,描述通知与切入点的对应关系（通知加切入点）
 *五.目标对象：通知所应用的对象
 */
@Aspect//切面类
@Component("globalOperatcionAspect")
public class GlobalOperatcionAspect {

    private static final Logger logger = LoggerFactory.getLogger(GlobalOperatcionAspect.class);


    private static final String TYPE_STRING = "java.lang.String";
    private static final String TYPE_INTEGER = "java.lang.Integer";
    private static final String TYPE_LONG = "java.lang.Long";

    @Pointcut("@annotation(com.CloudStorage.annotation.GlobalInterceptor)")
    private void requestInterceptor() {
    }

    @Before("requestInterceptor()")
    public void interceptorDo(JoinPoint point) throws BusinessException {
        try {
            //1.根据切入点获取原对象的参数
            Object target = point.getTarget();
            Object[] arguments = point.getArgs();
            String methodName = point.getSignature().getName();
            Class<?>[] parameterTypes = ((MethodSignature) point.getSignature()).getMethod().getParameterTypes();
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            //2.切入点方法的注解实例
            GlobalInterceptor interceptor = method.getAnnotation(GlobalInterceptor.class);
            if (null == interceptor) {
                return;
            }
            /**
             * 校验登录
             */
            if (interceptor.checkLogin() || interceptor.checkAdmin()) {
                checkLogin(interceptor.checkAdmin());
            }
            /**
             * 校验参数
             */
            if (interceptor.checkParams()) {
                validateParams(method, arguments);
            }
        } catch (BusinessException e) {
            logger.error("全局拦截器异常", e);
            throw e;
        } catch (Exception e) {
            logger.error("全局拦截器异常", e);
            throw new BusinessException(ResponseCodeEnum.CODE_500);
        } catch (Throwable e) {
            logger.error("全局拦截器异常", e);
            throw new BusinessException(ResponseCodeEnum.CODE_500);
        }
    }

    /**
     *
     * @param checkAdmin
     */
    private void checkLogin(Boolean checkAdmin) {
        //1.通过RequestContextHolder获取HttpServletRequest对象
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder
                .getRequestAttributes()).getRequest();
        //2.通过request获取HttpSession对象
        HttpSession session = request.getSession();
        //3.获取session中用于存储用户信息的对象
        SessionWebUserDto userDto = (SessionWebUserDto) session.getAttribute(Constants.SESSION_KEY);
        //4.如果未登录则抛出异常
        if (null == userDto) {
            throw new BusinessException(ResponseCodeEnum.CODE_901);
        }
        //5.如果申请的接口为超级管理员才能使用，则需要校验是否为超级管理员，如果不是则抛出异常
        if (checkAdmin && !userDto.getAdmin()) {
            throw new BusinessException(ResponseCodeEnum.CODE_404);
        }
    }

    /**
     * 校验参数
     * @param m
     * @param arguments
     * @throws BusinessException
     */
    private void validateParams(Method m, Object[] arguments) throws BusinessException {
        //1.获取方法的参数数组
        Parameter[] parameters = m.getParameters();
        //2.对方法的参数进行遍历，进行参数校验
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            //2.1获取参数的值（实参）
            Object value = arguments[i];
            //2.2获取参数的校验注解的实例
            VerifyParam verifyParam = parameter.getAnnotation(VerifyParam.class);
            if (verifyParam == null) {
                continue;
            }
            //2.3当参数是基本数据类型时
            if (TYPE_STRING.equals(parameter.getParameterizedType().getTypeName()) || TYPE_LONG.equals(parameter.getParameterizedType().getTypeName()) || TYPE_INTEGER.equals(parameter.getParameterizedType().getTypeName())) {
                checkValue(value, verifyParam);
                //2.4如果传递的是对象时
            } else {
                checkObjValue(parameter, value);
            }
        }
    }

    /**
     * 校验非基本数据类型的参数
     * @param parameter
     * @param value
     */
    private void checkObjValue(Parameter parameter, Object value) {
        try {
            //1.获取类名
            String typeName = parameter.getParameterizedType().getTypeName();
            //2.获取该类的实例对象
            Class classz = Class.forName(typeName);
            //3.获取该对象的字段
            Field[] fields = classz.getDeclaredFields();
            //4.遍历字段，对每个字段通过checkValue进行参数校验
            for (Field field : fields) {
                VerifyParam fieldVerifyParam = field.getAnnotation(VerifyParam.class);
                if (fieldVerifyParam == null) {
                    continue;
                }
                //4.1设置对对象所有字段的访问权限
                field.setAccessible(true);
                //4.1获取到字段的值
                Object resultValue = field.get(value);
                //4.2校验该字段
                checkValue(resultValue, fieldVerifyParam);
            }
        } catch (BusinessException e) {
            logger.error("校验参数失败", e);
            throw e;
        } catch (Exception e) {
            logger.error("校验参数失败", e);
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
    }

    /**
     * 校验基本数据类型的参数
     * @param value
     * @param verifyParam
     * @throws BusinessException
     */
    private void checkValue(Object value, VerifyParam verifyParam) throws BusinessException {
        Boolean isEmpty = value == null || StringTools.isEmpty(value.toString());
        Integer length = value == null ? 0 : value.toString().length();

        /**
         * 校验空
         */
        if (isEmpty && verifyParam.required()) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        /**
         * 校验长度
         */
        if (!isEmpty && (verifyParam.max() != -1 && verifyParam.max() < length || verifyParam.min() != -1 && verifyParam.min() > length)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        /**
         * 校验正则
         */
        if (!isEmpty && !StringTools.isEmpty(verifyParam.regex().getRegex()) && !VerifyUtils.verify(verifyParam.regex(), String.valueOf(value))) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
    }
}
