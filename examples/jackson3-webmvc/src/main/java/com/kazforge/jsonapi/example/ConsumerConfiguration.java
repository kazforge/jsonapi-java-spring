package com.kazforge.jsonapi.example;

import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.core.validation.EndpointIdentity;
import com.kazforge.jsonapi.jackson3.JsonApiJackson3;
import com.kazforge.jsonapi.patch.PatchPresence;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiResourceHttpMessageConverter;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiProtocolExceptionResolver;
import com.kazforge.jsonapi.spring.webmvc.media.JsonApiMediaNegotiationAdvice;
import com.kazforge.jsonapi.spring.webmvc.media.JsonApiMediaNegotiationInterceptor;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContextArgumentResolver;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContextInterceptor;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

/** Explicit application-owned runtime and Spring MVC wiring; no runtime discovery. */
@Configuration(proxyBeanMethods = false)
@EnableWebMvc
public class ConsumerConfiguration implements WebMvcConfigurer {
  private final JsonApi runtime;

  public ConsumerConfiguration(JsonApi runtime) {
    this.runtime = runtime;
  }

  @Bean
  public static JsonApi jsonApi() {
    return JsonApiJackson3.jsonApi(JsonMapper.builder().build());
  }

  @Bean
  public ArticleController articles() {
    return new ArticleController();
  }

  @Bean
  public OrdinaryController ordinary() {
    return new OrdinaryController();
  }

  @Bean
  public JsonApiMediaNegotiationAdvice negotiationAdvice() {
    return new JsonApiMediaNegotiationAdvice();
  }

  @Override
  public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
    builder.addCustomConverter(
        new JsonApiResourceHttpMessageConverter(
            runtime,
            request -> {
              Object attribute =
                  request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
              if (attribute instanceof Map<?, ?> variables
                  && variables.get("id") instanceof String id) {
                return new EndpointIdentity("articles", id);
              }
              return null;
            }));
  }

  @Override
  public Validator getValidator() {
    return new Validator() {
      @Override
      public boolean supports(Class<?> type) {
        return type == ArticlePatch.class;
      }

      @Override
      public void validate(Object target, Errors errors) {
        if (((ArticlePatch) target).title() instanceof PatchPresence.Present<String>(var title)
            && title != null
            && title.isBlank()) {
          errors.rejectValue("title", "blank", "A supplied title must be nonblank or null");
        }
      }
    };
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new JsonApiMediaNegotiationInterceptor());
    registry.addInterceptor(new JsonApiRequestContextInterceptor());
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new JsonApiRequestContextArgumentResolver());
  }

  @Override
  public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
    resolvers.addFirst(new JsonApiProtocolExceptionResolver(runtime));
  }
}
