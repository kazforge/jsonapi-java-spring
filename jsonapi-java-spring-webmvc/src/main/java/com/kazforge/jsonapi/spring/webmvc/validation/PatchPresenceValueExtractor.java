package com.kazforge.jsonapi.spring.webmvc.validation;

import com.kazforge.jsonapi.patch.PatchPresence;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;

/**
 * Extracts supplied PATCH values for Jakarta Validation type-use constraints and cascading.
 *
 * <p>Register explicitly with the application's Jakarta Validation configuration. Omitted members
 * emit nothing; supplied values, including null, are validated against the declared constraints.
 * This does not unwrap constraints on the wrapper itself or exempt children of supplied objects
 * from validation. Object-level constraints can inspect presence themselves.
 */
public final class PatchPresenceValueExtractor
    implements ValueExtractor<PatchPresence<@ExtractedValue ?>> {
  @Override
  public void extractValues(PatchPresence<?> originalValue, ValueReceiver receiver) {
    if (originalValue instanceof PatchPresence.Present<?>(var value)) {
      receiver.value(null, value);
    }
  }
}
