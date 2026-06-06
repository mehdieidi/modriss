package io.mehdieidi.modless.mdeevlcli;

import io.mehdieidi.modless.mde.validation.EvlValidationRequest;
import io.mehdieidi.modless.mde.validation.FileEvlModelConfiguration;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Runs arbitrary EVL modules against one file-backed EMF model.
 */
@Command(
        name = "run",
        mixinStandardHelpOptions = true,
        description = "Runs EVL validation with one read-only file-backed EMF model.")
public final class RunCommand implements Callable<Integer> {

    private final ValidationRunner runner = new ValidationRunner();

    @Spec
    private CommandSpec commandSpec;

    @Option(names = "--evl-root", required = true, description = "EVL entry file or directory root.")
    private Path evlRoot;

    @Option(names = "--module", split = ",", description = "Specific EVL module file(s), relative to --evl-root unless absolute.")
    private List<Path> modules = List.of();

    @Option(names = "--model", required = true, description = "Input XMI model file.")
    private Path model;

    @Option(names = "--metamodel", required = true, split = ",", description = "Ecore metamodel file. Repeat or comma-separate.")
    private List<Path> metamodels;

    @Option(names = "--model-name", required = true, description = "Epsilon model name used by EVL contexts, e.g. PIM.")
    private String modelName;

    @Option(names = "--aliases", split = ",", description = "Additional Epsilon model aliases.")
    private List<String> aliases = List.of();

    @Option(names = "--no-structural-validation", description = "Disable EMF structural validation while loading the model.")
    private boolean noStructuralValidation;

    @Option(names = "--fail-on-mandatory-violations", description = "Return exit code 3 when mandatory EVL constraints are violated.")
    private boolean failOnMandatoryViolations;

    @Option(names = "--fail-on-optional-violations", description = "Return exit code 3 when any EVL constraint or critique is violated.")
    private boolean failOnOptionalViolations;

    @Option(names = "--verbose", description = "Print captured EVL output and detailed element references.")
    private boolean verbose;

    @Option(names = "--log-file", description = "Write a JSON validation report to this file.")
    private Path logFile;

    /**
     * Builds and executes a validation request from command-line options.
     *
     * @return CLI validation exit code
     * @throws Exception if validation or report output fails
     */
    @Override
    public Integer call() throws Exception {
        EvlValidationRequest request = new EvlValidationRequest(
                evlRoot.toAbsolutePath().normalize(),
                modules,
                List.of(new FileEvlModelConfiguration(
                        modelName,
                        aliases,
                        model.toAbsolutePath().normalize(),
                        metamodels.stream().map(path -> path.toAbsolutePath().normalize())
                                .toList(),
                        !noStructuralValidation)),
                true);
        return runner.execute(commandSpec, request, failOnMandatoryViolations,
                failOnOptionalViolations, verbose, logFile);
    }
}
