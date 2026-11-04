package com.kazforge.jsonapi.example;

import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.patch.PatchPresence;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
        .body(new Article(id, input.title(), input.editor()));
  }

  /** Applies changes to the sample snapshot; a real application would load and persist it. */
  @PatchMapping("/articles/{id}")
  public Article update(@RequestBody @Validated ArticlePatch input) {
    Article current = one(input.id());
    return new Article(
        input.id(),
        input.title() instanceof PatchPresence.Present<String>(var title) ? title : current.title(),
        input.editor() instanceof PatchPresence.Present<ResourceIdentifier>(var editor)
            ? editor
            : current.editor());
  }

  @GetMapping("/articles/{id}")
  public Article one(@PathVariable("id") String id) {
    return new Article(id, "Hello JSON:API");
  }

  @GetMapping("/articles")
  public List<Article> many() {
    return List.of(one("42"));
  }

  @GetMapping("/articles/empty")
  public List<Article> empty() {
    return List.of();
  }
}
