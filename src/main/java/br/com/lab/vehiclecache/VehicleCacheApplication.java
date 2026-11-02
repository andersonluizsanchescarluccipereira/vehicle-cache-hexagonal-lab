package br.com.lab.vehiclecache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VehicleCacheApplication {
    public static void main(String[] args) {
        SpringApplication.run(VehicleCacheApplication.class, args);
    }
}
