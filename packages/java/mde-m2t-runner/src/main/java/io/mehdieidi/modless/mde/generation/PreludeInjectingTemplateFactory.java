package io.mehdieidi.modless.mde.generation;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.epsilon.egl.EglFileGeneratingTemplateFactory;
import org.eclipse.epsilon.egl.EglTemplate;
import org.eclipse.epsilon.egl.exceptions.EglRuntimeException;
import org.eclipse.epsilon.egl.incremental.IncrementalitySettings;
import org.eclipse.epsilon.egl.internal.IEglModule;
import org.eclipse.epsilon.egl.spec.EglTemplateSpecification;
import org.eclipse.epsilon.egl.traceability.Template;

final class PreludeInjectingTemplateFactory extends EglFileGeneratingTemplateFactory {

    private final String eglPrelude;

    PreludeInjectingTemplateFactory(Path outputRoot, Path libraryRoot) throws Exception {
        super(outputRoot);
        this.eglPrelude = buildPrelude(libraryRoot);
    }

    private static String buildPrelude(Path libraryRoot) throws Exception {
        if (!Files.isDirectory(libraryRoot)) {
            return "";
        }
        StringBuilder imports = new StringBuilder();
        for (String fileName : new String[]{
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
                imports.append("import \"")
                        .append(file.toAbsolutePath().normalize().toString().replace('\\', '/'))
                        .append("\";\n");
            }
        }
        if (imports.isEmpty()) {
            return "";
        }
        return "[%\n" + imports + "%]\n";
    }

    @Override
    protected EglTemplate createTemplate(EglTemplateSpecification spec) throws Exception {
        return super.createTemplate(new PreludeTemplateSpecification(spec, eglPrelude));
    }

    private static final class PreludeTemplateSpecification extends EglTemplateSpecification {

        private final EglTemplateSpecification delegate;
        private final String eglPrelude;

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

        @Override
        public Template createTemplate() {
            return delegate.createTemplate();
        }

        @Override
        public void parseInto(IEglModule module) throws Exception {
            URI uri = delegate.getURI();
            String templateText = Files.readString(Path.of(uri));
            module.parse(eglPrelude + templateText, new File(uri));
        }

        @Override
        public URI getURI() {
            return delegate.getURI();
        }
    }
}
