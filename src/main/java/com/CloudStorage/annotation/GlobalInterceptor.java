package com.CloudStorage.annotation;
import org.springframework.web.bind.annotation.Mapping;
import java.lang.annotation.*;


//全局校验
/*
ElementType.TYPE：可以应用于类、接口、枚举或注解类型。
ElementType.FIELD：可以应用于字段或属性。
ElementType.METHOD：可以应用于方法。
ElementType.PARAMETER：可以应用于方法的参数。
ElementType.CONSTRUCTOR：可以应用于构造函数。
ElementType.LOCAL_VARIABLE：可以应用于局部变量。
ElementType.PACKAGE：可以应用于包声明。
ElementType.ANNOTATION_TYPE：可以应用于注解类型。
ElementType.TYPE_PARAMETER：可以应用于类型参数（Java 8 引入）。
ElementType.TYPE_USE：可以应用于类型使用的任何地方（Java 8 引入）。
 */
//规定注解生效范围，在方法和类上生效
@Target({ElementType.METHOD, ElementType.TYPE})

//用于指定注解的保留策略。在这里，RUNTIME 表示注解在运行时保留，可以通过反射机制来访问和使用。
@Retention(RetentionPolicy.RUNTIME)

//@Documented 是一个 Java 注解，用于指示被注解的元素应该包含在生成的文档中。
//当我们在编写自定义注解时，通常希望该注解的信息能够出现在生成的 API 文档中，以便其他开发人员能够看到使用该注解的相关说明。
// 通过在自定义注解上添加 @Documented 注解，可以确保注解信息在生成的文档中可见。
// 请注意，@Documented 注解本身不会影响注解的行为或功能，它仅仅是一个标记注解，用于指示该注解是否应该出现在文档中。因此，
// 它只对定义的注解起作用，而不会影响被注解的元素本身。
@Documented

@Mapping
public @interface GlobalInterceptor {

    /**
     * 校验登录
     *
     * @return
     */
    boolean checkLogin() default true;

    /**
     * 校验参数
     *
     * @return
     */
    boolean checkParams() default false;

    /**
     * 校验管理员
     *
     * @return
     */
    boolean checkAdmin() default false;
}
