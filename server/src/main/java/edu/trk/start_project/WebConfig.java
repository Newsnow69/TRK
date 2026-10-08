package edu.trk.start_project;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Локальний режим: Spring сам віддає файли з папки client/, тож nginx та Docker не потрібні.
// Якщо папки немає (наприклад, у Docker-образі сервера), цей клас нічого не робить.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final Path clientDir;

    public WebConfig(@Value("${app.client-dir}") String dir) {
        this.clientDir = Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (Files.isDirectory(clientDir)) {
            // toUri() дає адресу виду file:///C:/.../client/ (з косою рискою в кінці)
            registry.addResourceHandler("/**").addResourceLocations(clientDir.toUri().toString());
        }
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        if (Files.isDirectory(clientDir)) {
            registry.addViewController("/").setViewName("forward:/index.html");
        }
    }
}