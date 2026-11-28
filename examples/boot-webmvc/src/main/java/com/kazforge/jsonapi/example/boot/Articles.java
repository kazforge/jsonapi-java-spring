package com.kazforge.jsonapi.example.boot;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Link;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.patch.PatchPresence;
import com.kazforge.jsonapi.spring.webmvc.JsonApiEndpoint;
import com.kazforge.jsonapi.spring.webmvc.JsonApiPatchTarget;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiNullableResponse;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiQueryParams;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.BindParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Stateless sample. The application owns resource loading and mutation. */
@RestController
@JsonApiEndpoint
@RequestMapping("/articles")
public class Articles {
  @JsonApiResource(type = "articles")
  public record Article(
      @JsonApiId @Nullable String id,
      @JsonApiAttribute @NotBlank @Nullable String title,
      @JsonApiRelationship @Nullable Person author) {}

  @JsonApiResource(type = "people")
  public record Person(@JsonApiId String id, @JsonApiAttribute String name) {}

  @JsonApiResource(type = "articles")
  public record Changes(
      @JsonApiId String id, @JsonApiAttribute PatchPresence<@NotBlank String> title) {}

  public record Page(@BindParam("page[size]") @Min(0) @Nullable Integer size) {}

  @GetMapping("/{id}")
  public Article one(@PathVariable("id") String id) {
    return new Article(id, "News", new Person("7", "Ada"));
  }

  @GetMapping
  public List<Article> list(@Valid @JsonApiQueryParams Page page) {
    return Integer.valueOf(0).equals(page.size()) ? List.of() : List.of(one("42"));
  }

  @GetMapping("/{id}/author")
  public JsonApiNullableResponse<Person> author(@PathVariable("id") String id) {
    return JsonApiNullableResponse.of("42".equals(id) ? new Person("7", "Ada") : null);
  }

  @GetMapping("/{id}/optional")
  public JsonApiNullableResponse<Article> optional(@PathVariable("id") String id) {
    return JsonApiNullableResponse.of("42".equals(id) ? one(id) : null);
  }

  @GetMapping("/{id}/optional-entity")
  public ResponseEntity<JsonApiNullableResponse<Article>> optionalEntity(
      @PathVariable("id") String id) {
    return ResponseEntity.accepted()
        .header("X-Nullable", "kept")
        .body(JsonApiNullableResponse.of("42".equals(id) ? one(id) : null));
  }

  @GetMapping("/{id}/optional-envelope")
  public JsonApiNullableResponse<Article> optionalEnvelope(@PathVariable("id") String id) {
    String self =
        UriComponentsBuilder.fromPath("/articles")
            .pathSegment(id, "optional-envelope")
            .build()
            .encode()
            .toUriString();
    return JsonApiNullableResponse.of("42".equals(id) ? one(id) : null)
        .withMeta(Meta.of(Map.of("source", "optional")))
        .withLinks(Links.ofLinks(Map.of("self", new Link.StringLink(self))))
        .withJsonApi(JsonApiObject.ofVersion("1.1"));
  }

  @PostMapping
  public ResponseEntity<Article> create(@Valid @RequestBody Article input) {
    return ResponseEntity.created(URI.create("/articles/42"))
        .body(new Article("42", input.title(), input.author()));
  }

  @PatchMapping("/{id}")
  @JsonApiPatchTarget("articles")
  public Article update(@Valid @RequestBody Changes changes) {
    Article current = one(changes.id());
    String title =
        changes.title() instanceof PatchPresence.Present<String>(var value)
            ? value
            : current.title();
    return new Article(current.id(), title, current.author());
  }
}
