package com.acme.flags.springboot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AutoConfigurationImportsFileTest {

    @Test
    void importsFile_declaresFlagsAutoConfiguration() throws IOException {
        String resource = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).as("resource %s should exist on the classpath", resource).isNotNull();
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            assertThat(content).isEqualTo("com.acme.flags.springboot.FlagsAutoConfiguration");
        }
    }
}
