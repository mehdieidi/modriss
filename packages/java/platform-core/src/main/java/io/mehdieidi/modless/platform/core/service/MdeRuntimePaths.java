package io.mehdieidi.modless.platform.core.service;

import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MdeRuntimePaths {

    private final MdeRuntimeOptions options;
    private volatile Path repositoryRoot;

    public MdeRuntimePaths(MdeRuntimeOptions options) {
        this.options = options == null ? MdeRuntimeOptions.defaults() : options;
    }

    public MdeRuntimeOptions options() {
        return options;
    }

    public Path repositoryRoot() {
        Path cached = repositoryRoot;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (repositoryRoot == null) {
                repositoryRoot = resolveRepositoryRoot();
            }
            return repositoryRoot;
        }
    }

    public Path validationRoot(ModelLevel level) {
        return resolveConfiguredRoot(options.validationRoot(), "mde/validation")
                .resolve(level.apiName()).normalize();
    }

    public Path validationEntryFile(ModelLevel level) {
        return switch (level) {
            case CIM -> Path.of("cim-semantic-validation.evl");
            case PIM -> Path.of("pim-semantic-validation.evl");
            case PSM -> Path.of("psm-semantic-validation.evl");
        };
    }

    public Path metamodelFile(ModelLevel level) {
        Path root = resolveConfiguredRoot(options.metamodelRoot(), "mde/metamodels");
        return root.resolve(level.apiName()).resolve(switch (level) {
            case CIM -> "cim-combined.ecore";
            case PIM -> "pim-combined.ecore";
            case PSM -> "psm-combined.ecore";
        }).normalize();
    }

    public Path cimToPimRoot() {
        return resolveConfiguredRoot(options.transformationRoot(), "mde/transformations")
                .resolve("cim-to-pim").normalize();
    }

    public Path pimToAwsPsmRoot() {
        return resolveConfiguredRoot(options.transformationRoot(), "mde/transformations")
                .resolve("pim-to-awspsm").normalize();
    }

    public Path generationRoot() {
        return resolveConfiguredRoot(options.generationRoot(), "mde/generation")
                .resolve("awspsm-to-artifacts").normalize();
    }

    private Path resolveConfiguredRoot(Path configured, String defaultRelative) {
        if (configured == null) {
            return repositoryRoot().resolve(defaultRelative).normalize();
        }
        Path absolute = configured.isAbsolute()
                ? configured.toAbsolutePath().normalize()
                : repositoryRoot().resolve(configured).normalize();
        if (!Files.isDirectory(absolute)) {
            throw new PlatformException(500, "Configured MDE asset directory does not exist: "
                    + absolute);
        }
        return absolute;
    }

    private Path resolveRepositoryRoot() {
        if (options.repositoryRoot() != null) {
            Path configured = options.repositoryRoot().toAbsolutePath().normalize();
            requireRepositoryRoot(configured);
            return configured;
        }

        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (isRepositoryRoot(current)) {
                return current;
            }
            current = current.getParent();
        }
        throw new PlatformException(500,
                "MDE assets were not found from the backend working directory.");
    }

    private void requireRepositoryRoot(Path root) {
        if (!isRepositoryRoot(root)) {
            throw new PlatformException(500, "Configured MDE repository root is missing required "
                    + "validation, transformation, generation, or metamodel assets: " + root);
        }
    }

    private boolean isRepositoryRoot(Path root) {
        return Files.isRegularFile(root.resolve("mde/validation/cim/cim-semantic-validation.evl"))
                && Files.isRegularFile(root.resolve(
                "mde/validation/pim/pim-semantic-validation.evl"))
                && Files.isRegularFile(root.resolve(
                "mde/validation/psm/psm-semantic-validation.evl"))
                && Files.isRegularFile(root.resolve(
                "mde/transformations/cim-to-pim/cim-to-pim.etl"))
                && Files.isRegularFile(root.resolve(
                "mde/transformations/pim-to-awspsm/pim-to-awspsm.etl"))
                && Files.isRegularFile(root.resolve(
                "mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx"))
                && Files.isRegularFile(root.resolve("mde/metamodels/cim/cim-combined.ecore"))
                && Files.isRegularFile(root.resolve("mde/metamodels/pim/pim-combined.ecore"))
                && Files.isRegularFile(root.resolve("mde/metamodels/psm/psm-combined.ecore"));
    }
}
