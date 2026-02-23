package com.venkat.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.venkat.rag", "com.venkat.tools"})
public class RagApplication {
  public static void main(String[] args) {
    SpringApplication.run(RagApplication.class, args);
  }
}
