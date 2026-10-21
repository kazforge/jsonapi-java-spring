package com.kazforge.jsonapi.example;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Unmarked handlers retain ordinary Spring JSON serialization. */
@RestController
public final class OrdinaryController {
  @GetMapping("/ordinary")
  public Map<String, String> ordinary() {
    return Map.of("message", "ordinary MVC");
  }

  @PatchMapping("/ordinary")
  public Article echo(@RequestBody Article input) {
    return input;
  }
}
