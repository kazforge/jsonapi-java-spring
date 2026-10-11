package com.kazforge.jsonapi.spring.webmvc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.core.SpringVersion;

class SpringVersionTest {
  @Test
  void testClasspathUsesRequestedSpringLine() {
    assertEquals(System.getProperty("spring.expectedVersion"), SpringVersion.getVersion());
  }
}
