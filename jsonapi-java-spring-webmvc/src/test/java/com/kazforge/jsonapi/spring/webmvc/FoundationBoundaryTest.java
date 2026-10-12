package com.kazforge.jsonapi.spring.webmvc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Checks compiled production dependencies without introducing an architecture-test runtime. */
class FoundationBoundaryTest {
  private static final List<String> FORBIDDEN =
      List.of(
          "com/fasterxml/jackson/",
          "tools/jackson/",
          "com/kazforge/jsonapi/jackson",
          "org/springframework/boot/",
          "/internal/");
  private static final List<String> PUBLIC_CORE =
      List.of(
          "com/kazforge/jsonapi/core/model/",
          "com/kazforge/jsonapi/core/validation/PrimaryDataContext");

  @Test
  void compiledProductionReferencesStayNeutralAndPublic() throws Exception {
    Path root =
        Path.of(JsonApiEndpoint.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    try (var paths = Files.walk(root)) {
      var classes = paths.filter(p -> p.toString().endsWith(".class")).toList();
      assertFalse(classes.isEmpty());
      for (Path file : classes) {
        try (var input = new DataInputStream(Files.newInputStream(file))) {
          assertEquals(0xcafebabe, input.readInt());
          input.readInt(); // minor and major version
          int count = input.readUnsignedShort();
          for (int index = 1; index < count; index++) {
            int tag = input.readUnsignedByte();
            switch (tag) {
              case 1 -> checkReference(input.readUTF(), file.toString());
              case 3, 4, 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4);
              case 5, 6 -> {
                input.skipNBytes(8);
                index++;
              }
              case 7, 8, 16, 19, 20 -> input.skipNBytes(2);
              case 15 -> input.skipNBytes(3);
              default -> fail("Unknown constant pool tag: " + tag);
            }
          }
        }
      }
    }
  }

  private static void checkReference(String reference, String origin) {
    for (String forbidden : FORBIDDEN) {
      assertFalse(reference.contains(forbidden), origin + " references " + reference);
    }
    for (var match :
        java.util.regex.Pattern.compile("com/kazforge/jsonapi/core/[A-Za-z0-9_/$]+")
            .matcher(reference)
            .results()
            .toList()) {
      assertTrue(
          PUBLIC_CORE.stream().anyMatch(allowed -> match.group().startsWith(allowed)),
          origin + " references unsupported core API " + reference);
    }
  }

  @Test
  void forbiddenReferencesAreDetected() {
    for (String reference : FORBIDDEN) {
      assertThrows(
          AssertionError.class, () -> checkReference("L" + reference + "Example;", "fixture"));
    }
    assertThrows(
        AssertionError.class,
        () -> checkReference("com/kazforge/jsonapi/core/validation/LocalValidation", "fixture"));
  }
}
