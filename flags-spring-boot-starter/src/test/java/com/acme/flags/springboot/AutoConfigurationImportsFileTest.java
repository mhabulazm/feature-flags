package com.acme.flags.springboot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;

class AutoConfigurationImportsFileTest {

    @Test
    void importsFile_declaresFlagsAutoConfiguration() {
        ImportCandidates candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        assertThat(candidates.getCandidates()).contains("com.acme.flags.springboot.FlagsAutoConfiguration");
    }
}
