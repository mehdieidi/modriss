package io.mehdieidi.modless.platform.core.service;

import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves repository-relative MDE asset paths from runtime options.
 */
public final class MdeRuntimePaths {

    /**
     * Runtime options that may override discovered asset roots.
     */
    private final MdeRuntimeOptions options;
    /**
     * Lazily discovered and cached repository root.
     */
    private volatile Path repositoryRoot;

    /**
     * Creates a runtime path resolver.
     *
     * @param options runtime options; {@code null} uses defaults
     */
    public MdeRuntimePaths(MdeRuntimeOptions options) {
        this.options = options == null ? MdeRuntimeOptions.defaults() : options;
    }

    /**
     * Returns the normalized runtime options.
     *
     * @return runtime options
     */
    public MdeRuntimeOptions options() {
        return options;
    }

    /**
     * Returns the configured or discovered repository root.
     *
     * @return repository root containing MDE assets
     */
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

    /**
     * Returns the validation root for a model level.
     *
     * @param level model level
     * @return validation asset directory
     */
    public Path validationRoot(ModelLevel level) {
        return resolveConfiguredRoot(options.validationRoot(), "mde/validation")
                .resolve(level.apiName()).normalize();
    }

    /**
     * Returns the entry EVL module name for a model level.
     *
     * @param level model level
     * @return level-specific EVL entry file
     */
    public Path validationEntryFile(ModelLevel level) {
        return switch (level) {
            case CIM -> Path.of("cim-semantic-validation.evl");
            case PIM -> Path.of("pim-semantic-validation.evl");
            case PSM -> Path.of("psm-semantic-validation.evl");
        };
    }

    /**
     * Returns the combined Ecore metamodel file for a model level.
     *
     * @param level model level
     * @return metamodel file path
     */
    public Path metamodelFile(ModelLevel level) {
        Path root = resolveConfiguredRoot(options.metamodelRoot(), "mde/metamodels");
        return root.resolve(level.apiName()).resolve(switch (level) {
            case CIM -> "cim-combined.ecore";
            case PIM -> "pim-combined.ecore";
            case PSM -> "psm-combined.ecore";
        }).normalize();
    }

    /**
     * Returns the bundled CIM-to-PIM transformation root.
     *
     * @return transformation directory
     */
    public Path cimToPimRoot() {
        return resolveConfiguredRoot(options.transformationRoot(), "mde/transformations")
                .resolve("cim-to-pim").normalize();
    }

    /**
     * Returns the bundled PIM-to-AWS-PSM transformation root.
     *
     * @return transformation directory
     */
    public Path pimToAwsPsmRoot() {
        return resolveConfiguredRoot(options.transformationRoot(), "mde/transformations")
                .resolve("pim-to-awspsm").normalize();
    }

    /**
     * Returns the bundled AWS PSM artifact generation root.
     *
     * @return generation directory
     */
    public Path generationRoot() {
        return resolveConfiguredRoot(options.generationRoot(), "mde/generation")
                .resolve("awspsm-to-artifacts").normalize();
    }

    /**
     * Resolves an optional configured root or falls back to a repository-relative default.
     *
     * @param configured      configured override path
     * @param defaultRelative default path relative to the repository root
     * @return normalized directory path
     */
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

    /**
     * Resolves the repository root from options or by walking up from the process working
     * directory.
     *
     * @return repository root
     */
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

    /**
     * Ensures an explicitly configured repository root contains required MDE assets.
     *
     * @param root configured repository root
     */
    private void requireRepositoryRoot(Path root) {
        if (!isRepositoryRoot(root)) {
            throw new PlatformException(500, "Configured MDE repository root is missing required "
                    + "validation, transformation, generation, or metamodel assets: " + root);
        }
    }

    /**
     * Checks for the set of assets required by the Java MDE services.
     *
     * @param root candidate repository root
     * @return {@code true} when all required assets exist
     */
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
