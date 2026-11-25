package com.kazforge.jsonapi.spring.boot;

import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.jackson3.JsonApiJackson3;
import com.kazforge.jsonapi.representation.RepresentationPolicy;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiDocumentResponseWriter;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiRelationshipHttpMessageConverter;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiResourceHttpMessageConverter;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiErrorHttpMessageConverter;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiProtocolExceptionResolver;
import com.kazforge.jsonapi.spring.webmvc.media.JsonApiMediaNegotiationAdvice;
import com.kazforge.jsonapi.spring.webmvc.media.JsonApiMediaNegotiationInterceptor;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiQueryParamsArgumentResolver;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContextArgumentResolver;
import com.kazforge.jsonapi.spring.webmvc.request.JsonApiRequestContextInterceptor;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import tools.jackson.databind.json.JsonMapper;

/** Installs JSON:API handler support in Boot-managed servlet MVC applications. */
@AutoConfiguration(after = {WebMvcAutoConfiguration.class, JacksonAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(WebMvcAutoConfiguration.class)
public final class JsonApiWebMvcAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  JsonApi jsonApi(JsonMapper mapper, ObjectProvider<RepresentationPolicy> policy) {
    return JsonApiJackson3.builder(mapper)
        .representationPolicy(policy.getIfAvailable(RepresentationPolicy::defaults))
        .build();
  }

  @Bean
  @ConditionalOnMissingBean
  JsonApiMediaNegotiationAdvice jsonApiMediaNegotiationAdvice() {
    return new JsonApiMediaNegotiationAdvice();
  }

  @Bean
  @ConditionalOnMissingBean
  JsonApiDocumentResponseWriter jsonApiDocumentResponseWriter(JsonApi runtime) {
    return new JsonApiDocumentResponseWriter(runtime);
  }

  // Boot already collects converter beans. Install only missing defaults, on the server builder.
  @Bean
  @ConditionalOnMissingBean(JsonApiResourceHttpMessageConverter.class)
  ServerHttpMessageConvertersCustomizer jsonApiResources(JsonApi runtime) {
    return builder -> builder.addCustomConverter(new JsonApiResourceHttpMessageConverter(runtime));
  }

  @Bean
  @ConditionalOnMissingBean(JsonApiRelationshipHttpMessageConverter.class)
  ServerHttpMessageConvertersCustomizer jsonApiRelationships(JsonApi runtime) {
    return builder ->
        builder.addCustomConverter(new JsonApiRelationshipHttpMessageConverter(runtime));
  }

  @Bean
  @ConditionalOnMissingBean(JsonApiErrorHttpMessageConverter.class)
  ServerHttpMessageConvertersCustomizer jsonApiErrors(JsonApi runtime) {
    return builder -> builder.addCustomConverter(new JsonApiErrorHttpMessageConverter(runtime));
  }

  @Bean
  WebMvcConfigurer jsonApiHandlerSupport() {
    return new WebMvcConfigurer() {
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
    };
  }

  @Bean
  WebMvcConfigurer jsonApiProtocolErrors(
      JsonApi runtime, ObjectProvider<JsonApiProtocolExceptionResolver> protocolResolver) {
    return new WebMvcConfigurer() {
      @Override
      public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
        var position = resolvers.listIterator();
        while (position.hasNext()) {
          if (position.next() instanceof ExceptionHandlerExceptionResolver) {
            break;
          }
        }
        position.add(
            protocolResolver.getIfAvailable(() -> new JsonApiProtocolExceptionResolver(runtime)));
      }
    };
  }
}
