package com.CloudStorage;

import com.CloudStorage.entity.config.AppConfig;
import com.CloudStorage.entity.constants.Constants;
import com.CloudStorage.spring.ApplicationContextProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.servlet.MultipartConfigElement;


@EnableAsync
@SpringBootApplication(scanBasePackages = {"com.CloudStorage"})
@MapperScan(basePackages = {"com.CloudStorage.mappers"})
@EnableTransactionManagement
@EnableScheduling
public class CloudStorageApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudStorageApplication.class, args);
    }

    @Bean
    @DependsOn({"applicationContextProvider"})
    MultipartConfigElement multipartConfigElement() {
        AppConfig appConfig = (AppConfig) ApplicationContextProvider.getBean("appConfig");
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setLocation(appConfig.getProjectFolder() + Constants.FILE_FOLDER_TEMP);
        return factory.createMultipartConfig();
    }
}
