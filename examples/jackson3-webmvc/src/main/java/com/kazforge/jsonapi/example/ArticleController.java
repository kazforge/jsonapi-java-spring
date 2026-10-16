package com.kazforge.jsonapi.example;

import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Stateless sample: real applications own persistence and identifier allocation. */
@RestController
@JsonApiEndpoint
public final class ArticleController {
  @PostMapping("/articles")
  public ResponseEntity<Article> create(@RequestBody Article input) {
    String id = input.id() == null ? "42" : input.id();
    return ResponseEntity.created(
            UriComponentsBuilder.fromPath("/articles").pathSegment(id).build().encode().toUri())
        .body(new Article(id, input.title()));
  }

  @GetMapping("/articles/42")
  public Article one() {
    return new Article("42", "Hello JSON:API");
  }

  @GetMapping("/articles")
  public List<Article> many() {
    return List.of(one());
  }

  @GetMapping("/articles/empty")
  public List<Article> empty() {
    return List.of();
  }
}
