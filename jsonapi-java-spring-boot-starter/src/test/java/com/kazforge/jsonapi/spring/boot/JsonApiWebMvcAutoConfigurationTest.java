package com.kazforge.jsonapi.spring.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.jackson3.JsonApiJackson3;
import com.kazforge.jsonapi.patch.PatchPresence;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiDocumentResponseWriter;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiRelationshipHttpMessageConverter;
import com.kazforge.jsonapi.spring.webmvc.document.JsonApiResourceHttpMessageConverter;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiErrorHttpMessageConverter;
import com.kazforge.jsonapi.spring.webmvc.error.JsonApiProtocolExceptionResolver;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.ClientHttpMessageConvertersCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.HandlerExceptionResolverComposite;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import tools.jackson.databind.json.JsonMapper;

class JsonApiWebMvcAutoConfigurationTest {
  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner().withUserConfiguration(Boot.class);

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class Boot {}

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  static class ManualMvc {}

  @JsonApiResource(type = "samples")
  public record Sample(@JsonApiId String id, @JsonApiAttribute String displayName) {}

  record Patch(PatchPresence<@NotBlank String> title) {}

  @Test
  void defaultsUseBootMapperAndOnlyCustomizeServerConverters() {
    runner
        .withPropertyValues("spring.jackson.property-naming-strategy=SNAKE_CASE")
        .run(context -> {
          assertThat(context)
              .hasSingleBean(JsonApi.class)
              .hasSingleBean(JsonApiDocumentResponseWriter.class);
          var json = context.getBean(JsonApi.class).resources().writeOne(new Sample("1", "sample"));
          assertThat(
                  context
                      .getBean(JsonMapper.class)
                      .readTree(json)
                      .at("/data/attributes/display_name")
                      .asString())
              .isEqualTo("sample");
          var server = context.getBean(RequestMappingHandlerAdapter.class).getMessageConverters();
          var client = HttpMessageConverters.forClient();
          context
              .getBeanProvider(ClientHttpMessageConvertersCustomizer.class)
              .orderedStream()
              .forEach(customizer -> customizer.customize(client));
          var clients = client.build();
          for (var type :
              List.of(
                  JsonApiResourceHttpMessageConverter.class,
                  JsonApiRelationshipHttpMessageConverter.class,
                  JsonApiErrorHttpMessageConverter.class)) {
            assertThat(server).filteredOn(type::isInstance).hasSize(1);
            assertThat(clients).noneMatch(type::isInstance);
          }
          assertExtractsSuppliedValues(context.getBean(Validator.class));
        });
  }

  @Test
  void respectsApplicationRuntimeValidatorConverterAndProtocolResolver() {
    var runtime = JsonApiJackson3.jsonApi(JsonMapper.builder().build());
    var converter = new JsonApiResourceHttpMessageConverter(runtime);
    var resolver = new JsonApiProtocolExceptionResolver(runtime);
    runner
        .withBean(JsonApi.class, () -> runtime)
        .withBean(LocalValidatorFactoryBean.class, LocalValidatorFactoryBean::new)
        .withBean(JsonApiResourceHttpMessageConverter.class, () -> converter)
        .withBean(JsonApiProtocolExceptionResolver.class, () -> resolver)
        .run(context -> {
          assertThat(context)
              .hasSingleBean(JsonApi.class)
              .hasSingleBean(JsonApiProtocolExceptionResolver.class);
          assertThat(context.getBean(JsonApi.class)).isSameAs(runtime);
          assertThat(context.getBean(RequestMappingHandlerAdapter.class).getMessageConverters())
              .filteredOn(JsonApiResourceHttpMessageConverter.class::isInstance)
              .containsExactly(converter);
          var chain =
              context.getBean("handlerExceptionResolver", HandlerExceptionResolverComposite.class);
          var resolvers = Objects.requireNonNull(chain.getExceptionResolvers());
          assertThat(resolvers)
              .filteredOn(JsonApiProtocolExceptionResolver.class::isInstance)
              .singleElement()
              .isSameAs(resolver);
          var applicationHandlers =
              resolvers.stream()
                  .filter(ExceptionHandlerExceptionResolver.class::isInstance)
                  .findFirst()
                  .orElseThrow();
          assertThat(resolvers).containsSequence(applicationHandlers, resolver);
          assertExtractsSuppliedValues(context.getBean(LocalValidatorFactoryBean.class));
        });
  }

  private static void assertExtractsSuppliedValues(Validator validator) {
    assertThat(validator.validate(new Patch(PatchPresence.omitted()))).isEmpty();
    assertThat(validator.validate(new Patch(PatchPresence.present(null)))).hasSize(1);
  }

  @Test
  void leavesAnApplicationResolverChainAheadOfProtocolFallback() {
    HandlerExceptionResolver first = (request, response, handler, exception) -> null;
    runner
        .withBean(
            WebMvcConfigurer.class,
            () ->
                new WebMvcConfigurer() {
                  @Override
                  @NullMarked
                  public void configureHandlerExceptionResolvers(
                      List<HandlerExceptionResolver> resolvers) {
                    resolvers.add(first);
                  }
                })
        .run(context -> {
          var chain =
              context.getBean("handlerExceptionResolver", HandlerExceptionResolverComposite.class);
          var resolvers = Objects.requireNonNull(chain.getExceptionResolvers());
          assertThat(resolvers).hasSize(2).startsWith(first);
          assertThat(resolvers.getLast()).isInstanceOf(JsonApiProtocolExceptionResolver.class);
        });
  }

  @Test
  void requiresBootManagedServletMvc() {
    new ApplicationContextRunner()
        .withUserConfiguration(Boot.class)
        .run(context -> assertThat(context).doesNotHaveBean(JsonApi.class));
    runner
        .withUserConfiguration(ManualMvc.class)
        .run(context -> assertThat(context).doesNotHaveBean(JsonApi.class));
  }
}
