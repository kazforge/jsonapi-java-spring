package com.kazforge.jsonapi.spring.webmvc.internal;

import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.core.model.ErrorSource;
import java.io.Serial;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Safe client-input diagnostics from typed query binding. */
public final class QueryBindingFailure extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;
  private final ProtocolError reason;
  private final @Nullable String[] parameters;

  public QueryBindingFailure(ProtocolError reason, @Nullable String... parameters) {
    super("Invalid query parameters");
    this.reason = reason;
    this.parameters = parameters.clone();
  }

  public List<ErrorObject> errors() {
    return Arrays.stream(parameters)
        .map(parameter ->
            reason.error(parameter == null ? null : ErrorSource.ofParameter(parameter)))
        .toList();
  }
}
