package com.kazforge.jsonapi.spring.webmvc.request;

import com.kazforge.jsonapi.query.JsonApiQuery;
import com.kazforge.jsonapi.query.JsonApiQueryException;
import com.kazforge.jsonapi.query.QueryDiagnostic;
import com.kazforge.jsonapi.spring.webmvc.internal.ProtocolError;
import com.kazforge.jsonapi.spring.webmvc.internal.QueryBindingFailure;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.web.bind.annotation.BindParam;

/** Exact constructor inputs and their validation sources for one invocation. */
final class QueryArguments {
  private static final TypeDescriptor STRING = TypeDescriptor.valueOf(String.class);
  private final JsonApiQuery query;
  private final Map<String, String> supplied = new HashMap<>();

  QueryArguments(JsonApiQuery query) {
    this.query = query;
  }

  Object construct(Class<?> type, ConversionService conversion) {
    var constructor = constructor(type);
    @Nullable Object[] args = new @Nullable Object[constructor.getParameterCount()];
    Map<String, Input> declared = new LinkedHashMap<>();
    boolean hasQuery = false;
    for (int i = 0; i < args.length; i++) {
      var parameter = new MethodParameter(constructor, i);
      if (parameter.getParameterType() == JsonApiQuery.class) {
        if (hasQuery || parameter.hasParameterAnnotation(BindParam.class)) {
          throw new IllegalStateException("Only one unannotated JsonApiQuery input is supported");
        }
        args[i] = query;
        hasQuery = true;
      } else {
        String name = opaqueName(parameter);
        if (declared.put(name, Input.of(parameter, conversion)) != null) {
          throw new IllegalStateException("Duplicate query input declaration: " + name);
        }
      }
    }

    Map<String, List<String>> remaining = new LinkedHashMap<>(query.filter());
    remaining.putAll(query.page());
    remaining.putAll(query.unprocessed());
    declared.forEach((name, input) -> {
      var values = remaining.remove(name);
      args[input.parameter().getParameterIndex()] = input.bind(name, values, conversion);
      var parameter = input.parameter().getParameter();
      if (values != null && parameter.isNamePresent()) {
        supplied.put(parameter.getName(), name);
      }
    });
    if (!remaining.isEmpty()) {
      throw failure(
          ProtocolError.UNSUPPORTED_QUERY_PARAMETER, remaining.keySet().iterator().next());
    }
    return BeanUtils.instantiateClass(constructor, args);
  }

  @Nullable String source(String field) {
    // Element violations address the same repeated URL parameter as their list.
    return supplied.get(field.replaceFirst("\\[\\d+]$", ""));
  }

  private static Constructor<?> constructor(Class<?> type) {
    if (type.isRecord()) {
      return Objects.requireNonNull(BeanUtils.findPrimaryConstructor(type));
    }
    var constructors = type.getConstructors();
    if (constructors.length != 1) {
      throw new IllegalStateException("Query DTO must have one public constructor");
    }
    return constructors[0];
  }

  private static String opaqueName(MethodParameter parameter) {
    var key = parameter.getParameterAnnotation(BindParam.class);
    if (key == null || key.value().isEmpty()) {
      throw new IllegalStateException("Query inputs require an explicit @BindParam name");
    }
    String name = key.value();
    if (name.equals("include")
        || name.equals("sort")
        || name.equals("fields")
        || name.startsWith("fields[")) {
      throw new IllegalStateException("Use JsonApiQuery for sort and representation selection");
    }
    return name;
  }

  private record Input(MethodParameter parameter, TypeDescriptor scalar, boolean repeated) {
    static Input of(MethodParameter parameter, ConversionService conversion) {
      var type = new TypeDescriptor(parameter);
      boolean list = type.getType() == List.class;
      var generic = parameter.getGenericParameterType();
      boolean concrete =
          list
              ? generic instanceof ParameterizedType p
                  && p.getActualTypeArguments()[0] instanceof Class<?>
              : generic instanceof Class<?>;
      if (!concrete) {
        throw new IllegalStateException(
            "Query inputs require a concrete scalar or List of scalars");
      }
      var scalar = list ? Objects.requireNonNull(type.getElementTypeDescriptor()) : type;
      if (scalar.isPrimitive()
          || scalar.getType() == Class.class
          || !BeanUtils.isSimpleValueType(scalar.getType())
          || !conversion.canConvert(STRING, scalar)) {
        throw new IllegalStateException(
            "Query inputs require a Spring-convertible simple reference type");
      }
      return new Input(parameter, scalar, list);
    }

    @Nullable Object bind(
        String name, @Nullable List<String> values, ConversionService conversion) {
      if (values == null) {
        return repeated ? List.of() : null;
      }
      if (repeated) {
        return values.stream().map(value -> convert(value, name, conversion)).toList();
      }
      if (values.size() != 1) {
        throw new JsonApiQueryException(
            QueryDiagnostic.INVALID_PARAMETER_CARDINALITY,
            name,
            "Expected one query parameter occurrence");
      }
      return convert(values.getFirst(), name, conversion);
    }

    private Object convert(String value, String name, ConversionService conversion) {
      if (scalar.getType() == String.class) {
        return value;
      }
      try {
        Object converted = conversion.convert(value, STRING, scalar);
        if (converted != null) {
          return converted;
        }
      } catch (ConversionFailedException ignored) {
        // Rejected values and converter messages are not public diagnostics.
      }
      throw failure(ProtocolError.INVALID_QUERY_PARAMETER, name);
    }
  }

  private static QueryBindingFailure failure(ProtocolError reason, String name) {
    return new QueryBindingFailure(List.of(QueryBindingFailure.error(reason, name)));
  }
}
