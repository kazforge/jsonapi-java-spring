package com.kazforge.jsonapi.example.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(proxyBeanMethods = false)
public class ArticleApplication {
  public static void main(String[] args) {
    SpringApplication.run(ArticleApplication.class, args);
  }
}
