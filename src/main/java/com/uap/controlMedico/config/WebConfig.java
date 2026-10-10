package com.uap.controlMedico.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    
        private final CommonModelInterceptor commonModelInterceptor;
 
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(commonModelInterceptor)
                .addPathPatterns("/admin/**", "/login")   // aplica a admin y login
                .excludePathPatterns(
                    "/api/**",          // no aplica a la API REST
                    "/css/**",
                    "/js/**",
                    "/img/**",
                    "/webjars/**"
                );
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/login")
                .setViewName("forward:/index.html");
        registry.addViewController("/admin/{*path}")
                .setViewName("forward:/index.html");
        registry.addViewController("/app/{*path}")
                .setViewName("forward:/index.html");
        registry.addViewController("/sisu/{*path}")
                .setViewName("forward:/index.html");
        registry.addViewController("/auth/{*path}")
                .setViewName("forward:/index.html");
        registry.addViewController("/monitoreo/{*path}")
                .setViewName("forward:/index.html");
        registry.addViewController("/personal-hospital/{*path}")
                .setViewName("forward:/index.html");
        registry.addViewController("/sistema/{*path}")
                .setViewName("forward:/index.html");
    }

}
