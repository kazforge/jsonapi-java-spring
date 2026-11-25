package com.kazforge.jsonapi.spring.webmvc.request;

import com.kazforge.jsonapi.spring.webmvc.internal.ProtocolError;
import com.kazforge.jsonapi.spring.webmvc.internal.QueryBindingFailure;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Conventions;
import org.springframework.core.MethodParameter;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Register in {@code WebMvcConfigurer#addArgumentResolvers} for {@link JsonApiQueryParams}. */
public final class JsonApiQueryParamsArgumentResolver implements HandlerMethodArgumentResolver {
  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(JsonApiQueryParams.class);
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      @Nullable ModelAndViewContainer container,
      NativeWebRequest webRequest,
      @Nullable WebDataBinderFactory binderFactory)
      throws Exception {
    var request = webRequest.getNativeRequest(HttpServletRequest.class);
    var context = request == null ? null : JsonApiRequestContext.find(request);
    if (context == null || binderFactory == null) {
      throw new IllegalStateException(
          "Typed query binding requires an opted-in endpoint, request context and MVC binder");
    }
    var name = Conventions.getVariableNameForParameter(parameter);
    var conversion =
        Objects.requireNonNull(
            binderFactory.createBinder(webRequest, null, name).getConversionService(),
            "Typed query binding requires the MVC conversion service");
    var inputs = new QueryArguments(context.query());
    Object target = inputs.construct(parameter.getParameterType(), conversion);
    if (parameter.hasParameterAnnotation(Valid.class)) {
      var binder = binderFactory.createBinder(webRequest, target, name);
      binder.validate();
      var errors = binder.getBindingResult().getAllErrors();
      if (!errors.isEmpty()) {
        throw new QueryBindingFailure(
            errors.stream()
                .map(error ->
                    QueryBindingFailure.error(
                        ProtocolError.INVALID_QUERY_PARAMETER,
                        error instanceof FieldError field ? inputs.source(field.getField()) : null))
                .toList());
      }
    }
    return target;
  }
}
