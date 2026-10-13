package io.mehdieidi.modriss.mde.generation;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.epsilon.egl.EglFileGeneratingTemplateFactory;
import org.eclipse.epsilon.egl.EglTemplate;
import org.eclipse.epsilon.egl.incremental.IncrementalitySettings;
import org.eclipse.epsilon.egl.internal.IEglModule;
import org.eclipse.epsilon.egl.spec.EglTemplateSpecification;
import org.eclipse.epsilon.egl.traceability.Template;

/** EGL template factory that prepends shared library imports to every template before parsing. */
final class PreludeInjectingTemplateFactory extends EglFileGeneratingTemplateFactory {

  /** EOL import block injected into every EGL template. */
  private final String eglPrelude;

  /**
   * Creates a factory for the requested output root and library import root.
   *
   * @param outputRoot directory where EGL writes generated files
   * @param libraryRoot directory containing shared EOL helper modules
   * @throws Exception when the base EGL factory cannot be initialized
   */
  PreludeInjectingTemplateFactory(Path outputRoot, Path libraryRoot) throws Exception {
    super(outputRoot);
    this.eglPrelude = buildPrelude(libraryRoot);
  }

  /**
   * Builds an EGL prelude that imports known helper libraries in dependency order.
   *
   * @param libraryRoot directory containing helper libraries
   * @return EGL prelude, or an empty string when no libraries are present
   * @throws Exception when helper paths cannot be resolved
   */
  private static String buildPrelude(Path libraryRoot) throws Exception {
    if (!Files.isDirectory(libraryRoot)) {
      return "";
    }
    StringBuilder imports = new StringBuilder();
    for (String fileName :
        new String[] {
          "naming.eol",
          "values.eol",
          "paths.eol",
          "iam.eol",
          "contracts.eol",
          "protected-regions.eol",
          "trace.eol",
          "validation.eol",
          "cfn.eol",
          "sam.eol"
        }) {
      Path file = libraryRoot.resolve(fileName);
      if (Files.isRegularFile(file)) {
        imports
            .append("import \"")
            .append(file.toAbsolutePath().normalize().toString().replace('\\', '/'))
            .append("\";\n");
      }
    }
    if (imports.isEmpty()) {
      return "";
    }
    return "[%\n" + imports + "%]\n";
  }

  /**
   * Wraps the template specification so parsing sees the injected prelude.
   *
   * @param spec original EGL template specification
   * @return parsed EGL template
   * @throws Exception when template creation fails
   */
  @Override
  protected EglTemplate createTemplate(EglTemplateSpecification spec) throws Exception {
    return super.createTemplate(new PreludeTemplateSpecification(spec, eglPrelude));
  }

  /**
   * Template specification wrapper that delegates metadata but prepends the shared prelude during
   * parse.
   */
  private static final class PreludeTemplateSpecification extends EglTemplateSpecification {

    /** Original template specification supplied by EGL. */
    private final EglTemplateSpecification delegate;

    /** Prelude text prepended before parsing template source. */
    private final String eglPrelude;

    /**
     * Creates a wrapper around an existing EGL template specification.
     *
     * @param delegate original template specification
     * @param eglPrelude prelude text to prepend
     */
    private PreludeTemplateSpecification(EglTemplateSpecification delegate, String eglPrelude) {
      super(
          delegate.getName(),
          delegate.getDefaultFormatter(),
          new IncrementalitySettings(delegate.getIncrementalitySettings()),
          delegate.getImportManager(),
          delegate.getTemplateExecutionListeners());
      this.delegate = delegate;
      this.eglPrelude = eglPrelude;
    }

    /**
     * Delegates template instantiation to the original specification.
     *
     * @return traceability template
     */
    @Override
    public Template createTemplate() {
      return delegate.createTemplate();
    }

    /**
     * Parses template source with the generated prelude prepended.
     *
     * @param module EGL module receiving parsed template content
     * @throws Exception when template source cannot be read or parsed
     */
    @Override
    public void parseInto(IEglModule module) throws Exception {
      URI uri = delegate.getURI();
      String templateText = Files.readString(Path.of(uri));
      module.parse(eglPrelude + templateText, new File(uri));
    }

    /**
     * Returns the original template URI so diagnostics still point at the source file.
     *
     * @return template URI
     */
    @Override
    public URI getURI() {
      return delegate.getURI();
    }
  }
}
