package io.mehdieidi.modless.mdecli.service;

import java.nio.file.Path;

public record ConversionRequest(
        Path input,
        Path output,
        Path rootFile,
        boolean overwrite,
        boolean verbose) {

}
