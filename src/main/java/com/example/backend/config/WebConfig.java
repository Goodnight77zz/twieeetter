package com.example.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    @Value("${file.upload.dir}")
    private String uploadDir;

    public WebConfig(LoginInterceptor loginInterceptor) {
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射规则：
        // 访问 http://localhost:8080/uploads/abc.jpg(commit更新测试)
        // 实际上去读取 D:/fyp_uploads/abc.jpg

        // 注意：Windows下文件路径必须以 file: 开头
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/",
                        "/login.html",
                        "/register.html",
                        "/index.html",
                        "/detail.html",
                        "/search.html",
                        "/dashboard.html",
                        "/profile.html",
                        "/content.html",
                        "/archive.html",
                        "/friends.html",
                        "/notifications.html",
                        "/uploads/**",
                        "/common/**",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/favicon.ico"
                );
    }
}
