package io.mehdieidi.modriss.mde.emf;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.Locale;

/** Converts EDate values without EMF's lenient, JVM-timezone-dependent date parsing. */
public final class EmfDateTime {

  // Numeric UTC offsets also remain readable by older EMF clients that misinterpret trailing Z.
  private static final DateTimeFormatter OUTPUT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSxx", Locale.ROOT)
          .withZone(ZoneOffset.UTC);

  private EmfDateTime() {}

  /**
   * Parses ISO timestamps and EMF's compact offsets. Legacy values without an offset use UTC,
   * including date-only values, so the same model has the same meaning on every host.
   */
  public static Date parse(String value) {
    if (value == null) return null;
    String text = value.trim().replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2");
    if (!text.contains("T")) {
      return Date.from(LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant());
    }
    TemporalAccessor parsed =
        DateTimeFormatter.ISO_DATE_TIME.parseBest(
            text, Instant::from, LocalDateTime::from);
    return Date.from(
        parsed instanceof Instant instant
            ? instant
            : ((LocalDateTime) parsed).toInstant(ZoneOffset.UTC));
  }

  /** Serializes an EDate to a deterministic UTC timestamp at Date's millisecond precision. */
  public static String format(Date value) {
    return value == null ? null : OUTPUT.format(Instant.ofEpochMilli(value.getTime()));
  }
}
