package io.gengdy.pan.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "file:${pan.home}/etc/pan.properties", ignoreResourceNotFound = false)
public class AppConfig
{
}
