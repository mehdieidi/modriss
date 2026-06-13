package io.mehdieidi.modless.mdecli.service;

import java.nio.file.Path;

/**
 * Options for an Emfatic-to-Ecore conversion.
 *
 * @param input input Emfatic file or module directory
 * @param output optional output Ecore path
 * @param rootFile optional root module for directory conversion
 * @param overwrite whether an existing output may be replaced
 * @param verbose whether detailed execution events should be printed
 */
public record ConversionRequest(
    Path input, Path output, Path rootFile, boolean overwrite, boolean verbose) {}
