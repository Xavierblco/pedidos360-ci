package com.pedidos360.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MensajeriaAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(MensajeriaAdminApplication.class, args);
    }
}
