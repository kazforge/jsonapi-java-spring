package com.kazforge.jsonapi.example;

import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.core.validation.EndpointIdentity;
import com.kazforge.jsonapi.jackson3.JsonApiJackson3;
import com.kazforge.jsonapi.representation.IncludePolicy;
import com.kazforge.jsonapi.representation.RelationshipAllowance;
import com.kazforge.jsonapi.representation.RepresentationPolicy;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiRelationshipHttpMessageConverter;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiResourceHttpMessageConverter;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiErrorHttpMessageConverter;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiProtocolExceptionResolver;
import com.kazforge.jsonapi.spring.webmvc.media.JsonApiMediaNegotiationAdvice;
import com.kazforge.jsonapi.spring.webmvc.media.JsonApiMediaNegotiationInterceptor;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiQueryParamsArgumentResolver;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContextArgumentResolver;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContextInterceptor;
import com.kazforge.jsonapi.spring.webmvc.validation.PatchPresenceValueExtractor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
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
  private static final String ARTICLES = "articles";
  private final JsonApi runtime;

  public ConsumerConfiguration(JsonApi runtime) {
    this.runtime = runtime;
  }

  @Bean
  public static JsonApi jsonApi() {
    var includes =
        IncludePolicy.allowing(
            Set.of(
                RelationshipAllowance.of(ARTICLES, "author"),
                RelationshipAllowance.of(ARTICLES, "editor")));
    return JsonApiJackson3.builder(JsonMapper.builder().build())
        .representationPolicy(RepresentationPolicy.defaults().withIncludePolicy(includes))
        .build();
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
    builder.addCustomConverter(new JsonApiErrorHttpMessageConverter(runtime));
    builder.addCustomConverter(new JsonApiRelationshipHttpMessageConverter(runtime));
    builder.addCustomConverter(
        new JsonApiResourceHttpMessageConverter(
            runtime,
            request -> {
              Object attribute =
                  request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
              if (attribute instanceof Map<?, ?> variables
                  && variables.get("id") instanceof String id) {
                return new EndpointIdentity(ARTICLES, id);
              }
              return null;
            }));
  }

  @Override
  public LocalValidatorFactoryBean getValidator() {
    var validator = new LocalValidatorFactoryBean();
    validator.setConfigurationInitializer(
        configuration -> configuration.addValueExtractor(new PatchPresenceValueExtractor()));
    return validator;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new JsonApiMediaNegotiationInterceptor());
    registry.addInterceptor(new JsonApiRequestContextInterceptor());
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new JsonApiRequestContextArgumentResolver());
    resolvers.add(new JsonApiQueryParamsArgumentResolver());
  }

  @Override
  public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
    // After Spring's @ExceptionHandler resolver, but before its other defaults.
    resolvers.add(1, new JsonApiProtocolExceptionResolver(runtime));
  }
}
