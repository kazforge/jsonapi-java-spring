package com.kazforge.jsonapi.example;

import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.jackson3.JsonApiJackson3;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiResourceHttpMessageConverter;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiProtocolExceptionResolver;
import com.kazforge.jsonapi.spring.webmvc.media.JsonApiMediaNegotiationAdvice;
import com.kazforge.jsonapi.spring.webmvc.media.JsonApiMediaNegotiationInterceptor;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContextArgumentResolver;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContextInterceptor;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerExceptionResolver;
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
    builder.addCustomConverter(new JsonApiResourceHttpMessageConverter(runtime));
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
