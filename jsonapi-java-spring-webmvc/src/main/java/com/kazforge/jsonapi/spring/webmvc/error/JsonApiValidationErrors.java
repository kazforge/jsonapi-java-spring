package com.kazforge.jsonapi.spring.webmvc.error;

import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.core.model.ErrorSource;
import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.mapping.BoundResource;
import com.kazforge.jsonapi.patch.PatchPresence;
import com.kazforge.jsonapi.spring.webmvc.internal.JsonApiBodyBinding;
import com.kazforge.jsonapi.spring.webmvc.internal.JsonApiHandlers;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/** Adapts validation paths to Core's logical source lookup without inspecting the DTO. */
final class JsonApiValidationErrors {
  private static final Pattern SPRING_SEGMENT =
      Pattern.compile("(?:^|\\.)([A-Za-z_$][A-Za-z0-9_$]*)|\\[(0|[1-9][0-9]*)\\]");

  private JsonApiValidationErrors() {}

  static Optional<JsonApiErrorResponse> fromValidation(
      HttpServletRequest request, MethodArgumentNotValidException error) {
    if (!(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE)
        instanceof HandlerMethod method)) {
      return Optional.empty();
    }
    var endpoint = JsonApiHandlers.findEndpoint(method);
    var parameter = error.getParameter();
    var result = error.getBindingResult();
    if (endpoint == null
        || endpoint.primaryDataContext() != PrimaryDataContext.RESOURCE
        || !BridgeMethodResolver.findBridgedMethod(method.getMethod()).equals(parameter.getMethod())
        || !parameter.hasParameterAnnotation(RequestBody.class)
        || !result.hasErrors()) {
      return Optional.empty();
    }
    BoundResource<?> bound = JsonApiBodyBinding.find(request, result.getTarget());
    return bound == null ? Optional.empty() : Optional.of(map(result, bound));
  }

  static JsonApiErrorResponse map(BindingResult result, BoundResource<?> bound) {
    return JsonApiErrorResponse.of(
        result.getAllErrors().stream()
            .map(error -> {
              boolean field = error instanceof FieldError;
              return ErrorObject.builder()
                  .code(field ? "INVALID_FIELD" : "INVALID_RESOURCE")
                  .title(field ? "Invalid field value" : "Invalid resource")
                  .source(source(error, bound))
                  .build();
            })
            .toList());
  }

  private static @Nullable ErrorSource source(ObjectError error, BoundResource<?> bound) {
    List<String> path = logicalPath(error);
    if (path == null) {
      return null;
    }
    // An empty path is only a candidate for a violation of the root object itself.
    int shortest = path.isEmpty() ? 0 : 1;
    for (int length = path.size(); length >= shortest; length--) {
      MappingLocation location = bound.source(List.copyOf(path.subList(0, length))).location();
      if (location != null) {
        return ErrorSource.builder().pointer(location.pointer()).build();
      }
    }
    return null;
  }

  private static @Nullable List<String> logicalPath(ObjectError error) {
    if (error.contains(ConstraintViolation.class)) {
      return constraintPath(error.unwrap(ConstraintViolation.class).getPropertyPath());
    }
    if (error instanceof FieldError field) {
      return springPath(field.getField());
    }
    return List.of();
  }

  private static @Nullable List<String> constraintPath(Path path) {
    List<String> segments = new ArrayList<>();
    for (Path.Node node : path) {
      // In cascaded validation, a property/bean node can carry the enclosing element's
      // index too. Never traverse an opaque iteration or treat a map key as an index.
      ElementKind kind = node.getKind();
      if ((kind != ElementKind.PROPERTY
              && kind != ElementKind.BEAN
              && kind != ElementKind.CONTAINER_ELEMENT)
          || !appendIndex(node, segments)) {
        break;
      }
      if (kind == ElementKind.BEAN) {
        return segments; // Bean nodes are leaves; a root bean has no segments.
      }
      if (kind == ElementKind.PROPERTY) {
        String name = node.getName();
        if (name == null || name.isEmpty()) {
          break;
        }
        segments.add(name);
      } else if (!node.isInIterable() && !isTransparentWrapper(node)) {
        break;
      }
    }
    return segments.isEmpty() ? null : segments;
  }

  private static boolean appendIndex(Path.Node node, List<String> segments) {
    if (!node.isInIterable()) {
      return true;
    }
    Integer index = node.getIndex();
    if (node.getKey() != null || index == null || index < 0) {
      return false;
    }
    segments.add(index.toString());
    return true;
  }

  private static boolean isTransparentWrapper(Path.Node node) {
    Class<?> container = node.as(Path.ContainerElementNode.class).getContainerClass();
    return container == Optional.class || container == PatchPresence.class;
  }

  private static @Nullable List<String> springPath(String field) {
    List<String> segments = new ArrayList<>();
    var matcher = SPRING_SEGMENT.matcher(field);
    int end = 0;
    while (matcher.find() && matcher.start() == end) {
      String property = matcher.group(1);
      if (end == 0 && (property == null || field.startsWith("."))) {
        break;
      }
      end = matcher.end();
      if (end < field.length() && field.charAt(end) != '.' && field.charAt(end) != '[') {
        break;
      }
      // Only canonical bracketed indices are accepted. Keys and other syntax stop traversal.
      segments.add(property != null ? property : matcher.group(2));
    }
    return segments.isEmpty() ? null : segments;
  }
}
