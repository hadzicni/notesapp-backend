package ch.hadzic.nikola.notesapp.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.*;

class DateFormatUtilTest {

  @Test
  void format_roundtrips_withParse() {
    LocalDateTime dt = LocalDateTime.of(2025, 11, 11, 22, 0);
    String s = DateFormatUtil.format(dt);
    assertEquals("11.11.2025 22:00", s);

    LocalDateTime parsed = DateFormatUtil.parse(s);
    assertEquals(dt, parsed);
  }

  @Test
  void parse_boundary_values_and_invalid() {
    // boundary - earliest sensible date in many systems
    LocalDateTime min = LocalDateTime.of(1970, 1, 1, 0, 0);
    assertEquals(min, DateFormatUtil.parse("01.01.1970 00:00"));

    // invalid format
    assertThrows(DateTimeParseException.class, () -> DateFormatUtil.parse("2025-11-11T22:00"));
    // invalid values
    assertThrows(DateTimeParseException.class, () -> DateFormatUtil.parse("32.13.2025 99:99"));
    // null handling
    assertThrows(NullPointerException.class, () -> DateFormatUtil.parse(null));
  }

  // TDD – Open bug: Parsing should ignore leading/trailing whitespaces.
  // This test defines expected behavior but currently fails.
  // Tagged to be excluded in CI.
  @org.junit.jupiter.api.Tag("tdd-open-bug")
  @Test
  void parse_should_trim_whitespace() {
    LocalDateTime expected = LocalDateTime.of(2025, 11, 11, 22, 0);
    // wirft DateTimeParseException, weil nicht getrimmt wird
    LocalDateTime parsed = DateFormatUtil.parse(" 11.11.2025 22:00 \t");
    assertEquals(expected, parsed);
  }
}
