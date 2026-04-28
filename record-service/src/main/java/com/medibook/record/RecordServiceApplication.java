package com.medibook.record;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RecordServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RecordServiceApplication.class, args);
        System.out.println("✅ MediBook Record Service started on port 8088");
    }
}