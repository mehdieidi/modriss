package io.mehdieidi.modriss.mde.emf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Date conversion must preserve instants even when the process uses a non-UTC timezone. */
@Isolated("Changes the JVM default timezone")
class EmfDateTimeTest {

  @ParameterizedTest
  @ValueSource(strings = {"UTC", "Asia/Tehran", "America/Los_Angeles"})
  void preservesInstantsAndCanonicalXmiAcrossTimezones(String timezone) throws Exception {
    TimeZone previous = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone(timezone));
      Instant expected = Instant.parse("2026-06-15T00:00:00.123Z");
      byte[] canonical = null;
      for (String timestamp :
          new String[] {
            "2026-06-15T00:00:00.123Z",
            "2026-06-15T00:00:00.123+0000",
            "2026-06-15T03:30:00.123+03:30",
            "2026-06-15T03:30:00.123+0330",
            "2026-06-14T17:00:00.123-07:00",
            "2026-06-15T02:00:00.123+02:00[Europe/Paris]",
            "2026-06-15T00:00:00.123"
          }) {
        assertEquals(expected, EmfDateTime.parse(timestamp).toInstant(), timestamp);
        Resource resource = load(timestamp);
        EAttribute attribute =
            (EAttribute) resource.getContents().get(0).eClass().getEStructuralFeature("at");
        assertEquals(
            expected, ((Date) resource.getContents().get(0).eGet(attribute)).toInstant(), timestamp);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        resource.save(output, Map.of());
        assertTrue(
            output.toString(StandardCharsets.UTF_8)
                .contains("at=\"2026-06-15T00:00:00.123+0000\""));
        if (canonical == null) canonical = output.toByteArray();
        assertArrayEquals(canonical, output.toByteArray(), timestamp);
      }
      assertEquals("2026-06-15T00:00:00.123+0000", EmfDateTime.format(Date.from(expected)));
      for (String timestamp :
          new String[] {"2026-06-15", "2026-06-15T00:00", "2026-06-15T00:00:00Z"}) {
        assertEquals(Instant.parse("2026-06-15T00:00:00Z"), EmfDateTime.parse(timestamp).toInstant());
      }
    } finally {
      TimeZone.setDefault(previous);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2026-02-30T00:00:00Z",
        "2026-06-15T00:00:00.000Zgarbage",
        "2026-06-15T00:00:00+25:00",
        "2026-06-15junk"
      })
  void rejectsInvalidDatesInsteadOfSilentlyTruncatingThem(String timestamp) {
    assertThrows(DateTimeParseException.class, () -> EmfDateTime.parse(timestamp));
  }

  private Resource load(String timestamp) throws Exception {
    EcoreFactory factory = EcoreFactory.eINSTANCE;
    EPackage schema = factory.createEPackage();
    schema.setName("dates");
    schema.setNsPrefix("dates");
    schema.setNsURI("urn:dates");
    EClass record = factory.createEClass();
    record.setName("Record");
    EAttribute at = factory.createEAttribute();
    at.setName("at");
    at.setEType(EcorePackage.Literals.EDATE);
    record.getEStructuralFeatures().add(at);
    schema.getEClassifiers().add(record);
    ResourceSetImpl resourceSet = new ResourceSetImpl();
    resourceSet.getPackageRegistry().put(schema.getNsURI(), schema);
    Resource resource = new DateAwareXmiResource(URI.createURI("memory:/date.xmi"));
    resourceSet.getResources().add(resource);
    String xml = "<dates:Record xmlns:dates=\"urn:dates\" at=\"" + timestamp + "\"/>";
    resource.load(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), Map.of());
    assertEquals(0, resource.getErrors().size(), resource.getErrors().toString());
    return resource;
  }
}
