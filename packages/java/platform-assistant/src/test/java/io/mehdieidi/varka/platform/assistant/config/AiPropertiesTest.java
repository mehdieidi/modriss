package io.mehdieidi.varka.platform.assistant.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AiPropertiesTest {

  @Test
  void normalizesOpenAiCompatibleBaseUrlsToTheVersionedApiRoot() {
    assertEquals(
        "http://host.docker.internal:3001/v1",
        new AiProperties.OpenAiCompatible("http://host.docker.internal:3001", "key").baseUrl());
    assertEquals(
        "https://api.example.test/v1",
        new AiProperties.OpenAiCompatible("https://api.example.test/v1/", "key").baseUrl());
  }

  @Test
  void retainsTheExplicitStrictJsonSchemaProtocol() {
    assertEquals(
        AiProperties.OpenAiProtocol.JSON_SCHEMA,
        new AiProperties.OpenAiCompatible(
                "https://api.example.test/v1", "key", AiProperties.OpenAiProtocol.JSON_SCHEMA)
            .protocol());
  }
}
