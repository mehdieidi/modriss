package io.mehdieidi.modless.mdecli.service;

import io.mehdieidi.modless.mdecli.diagnostics.ConversionReport;
import io.mehdieidi.modless.mdecli.diagnostics.DiagnosticEntry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;

public final class EmfConversionService {

    private final PathDecider pathDecider = new PathDecider();
    private final EmfaticImportScanner importScanner = new EmfaticImportScanner();
    private final EmfResourceSupport resourceSupport = new EmfResourceSupport();
    private final EmfaticCompiler emfaticCompiler = new EmfaticCompiler();
    private final DiagnosticMapper diagnosticMapper = new DiagnosticMapper();
    private final EcoreCombiner ecoreCombiner = new EcoreCombiner();
    private final EcoreModelNormalizer ecoreModelNormalizer = new EcoreModelNormalizer();
    private final StubMetamodelExtractor stubMetamodelExtractor = new StubMetamodelExtractor();
    private final StubEcoreGenerator stubEcoreGenerator = new StubEcoreGenerator();

    public ConversionReport convert(ConversionRequest request) {
        Path input = request.input().toAbsolutePath().normalize();
        Path output = pathDecider.resolveOutput(request);
        ConversionReport report = new ConversionReport();
        report.recordEvent("Input: " + input);
        report.recordEvent("Output: " + output);

        validateRequest(input, output, request.overwrite(), report);

        try {
            if (Files.isDirectory(input)) {
                convertDirectory(input, output, request, report);
            } else {
                convertSingleFile(input, output, report);
            }
            report.succeed("Conversion completed successfully.", output);
            return report;
        } catch (ConversionException ex) {
            throw ex;
        } catch (Exception ex) {
            report.addDiagnostic(diagnosticMapper.fromException(input.toString(), ex));
            report.fail(
                    "The CLI could not convert the metamodel.",
                    "Inspect the reported path and diagnostics. If the input is modular, prefer directory conversion and provide --root when needed.",
                    ex);
            throw new ConversionException(ex.getMessage(), report, true, ex);
        }
    }

    private void convertSingleFile(Path input, Path output, ConversionReport report)
            throws IOException {
        ModuleDescriptor descriptor = importScanner.scan(input);
        if (!descriptor.importedEcoreFiles().isEmpty()) {
            report.recordEvent("Running modular single-file conversion.");
            convertModularSingleFile(input, output, report);
            return;
        }

        report.recordEvent("Running standalone single-file conversion.");
        ResourceSet resourceSet = resourceSupport.newResourceSet();
        Resource inputResource = loadEmfaticResource(resourceSet, input, report);
        saveAsEcore(output, inputResource.getContents(), report);
    }

    private void convertDirectory(Path directory, Path output, ConversionRequest request,
            ConversionReport report) throws IOException {
        report.recordEvent("Running modular directory conversion.");
        Path rootFile = pathDecider.resolveRootFile(directory, request.rootFile());
        report.recordEvent("Resolved root file: " + rootFile);

        PreparedWorkspace workspace = prepareWorkspace(List.of(rootFile), "mde-cli-ecore-", report);
        Path tempDirectory = workspace.tempDirectory();

        List<ModuleDescriptor> modules = workspace.modules().stream()
                .sorted(Comparator.comparing(
                        module -> module.sourceFile().getFileName().toString()))
                .toList();
        Path tempRootFile = workspace.requireStagedPath(rootFile);
        generateStubEcores(modules, report);
        compileModulesIteratively(modules, report);

        Path generatedRootEcore = tempRootFile.resolveSibling(
                replaceExtension(tempRootFile.getFileName().toString(), ".ecore"));
        if (Files.notExists(generatedRootEcore)) {
            throw new IllegalStateException("The root module was not compiled: " + rootFile);
        }

        report.recordEvent("Combining compiled packages into a single Ecore resource.");
        ResourceSet ecoreResourceSet = resourceSupport.newResourceSet();
        List<Resource> compiledResources = loadCompiledResources(ecoreResourceSet, tempDirectory,
                generatedRootEcore);
        List<EObject> combinedContents = ecoreCombiner.combine(compiledResources);
        saveAsEcore(output, combinedContents, report);
    }

    private void convertModularSingleFile(Path input, Path output, ConversionReport report)
            throws IOException {
        Path absoluteInput = input.toAbsolutePath().normalize();
        Path directory = absoluteInput.getParent();
        if (directory == null) {
            throw new IOException(
                    "The input file has no parent directory to resolve imported modules from.");
        }

        PreparedWorkspace workspace = prepareWorkspace(List.of(absoluteInput),
                "mde-cli-single-ecore-", report);
        Path tempDirectory = workspace.tempDirectory();
        List<ModuleDescriptor> modules = workspace.modules().stream()
                .sorted(Comparator.comparing(
                        module -> module.sourceFile().getFileName().toString()))
                .toList();
        generateStubEcores(modules, report);
        compileModulesIteratively(modules, report);

        Path tempCompiledTarget = workspace.requireStagedPath(absoluteInput)
                .resolveSibling(replaceExtension(absoluteInput.getFileName().toString(), ".ecore"));
        if (Files.notExists(tempCompiledTarget)) {
            throw new IOException("The requested module was not compiled into an .ecore file: "
                    + absoluteInput.getFileName());
        }
        materializeModularSingleFileOutputs(tempDirectory, modules, absoluteInput, output, report);
    }

    private PreparedWorkspace prepareWorkspace(
            Collection<Path> seedFiles,
            String tempPrefix,
            ConversionReport report) throws IOException {
        WorkspacePlan plan = planWorkspace(seedFiles);
        Path tempDirectory = Files.createTempDirectory(tempPrefix);
        report.recordEvent("Created temp workspace: " + tempDirectory);

        Map<Path, Path> stagedPaths = new LinkedHashMap<>();
        for (Path moduleFile : plan.moduleFiles()) {
            Path stagedPath = copyIntoWorkspace(moduleFile, plan.workspaceRoot(), tempDirectory,
                    report);
            stagedPaths.put(moduleFile, stagedPath);
        }
        for (Path externalEcore : plan.externalEcoreFiles()) {
            copyIntoWorkspace(externalEcore, plan.workspaceRoot(), tempDirectory, report);
        }

        List<ModuleDescriptor> stagedModules = stagedPaths.values().stream()
                .map(importScanner::scan)
                .toList();
        return new PreparedWorkspace(tempDirectory, stagedPaths, stagedModules);
    }

    private Resource loadEmfaticResource(ResourceSet resourceSet, Path path,
            ConversionReport report) throws IOException {
        EmfaticCompiler.CompilationResult result = emfaticCompiler.compile(path);
        result.diagnostics().forEach(report::addDiagnostic);
        if (result.hasErrors()) {
            throw new IOException(buildHumanReadableFailure(path, result.diagnostics()));
        }
        Resource resource = resourceSet.createResource(URI.createFileURI(path.toString()));
        resource.getContents().addAll(ecoreCombiner.copy(result.resource().getContents()));
        return resource;
    }

    private void compileModulesIteratively(List<ModuleDescriptor> modules, ConversionReport report)
            throws IOException {
        int maxPasses = Math.max(2, modules.size());
        List<String> failedInLastPass = new ArrayList<>();

        for (int pass = 1; pass <= maxPasses; pass++) {
            report.recordEvent("Compilation pass " + pass + " of " + maxPasses + ".");
            failedInLastPass.clear();

            for (ModuleDescriptor module : modules) {
                Path moduleFile = module.sourceFile();
                Path generatedModuleEcore = moduleFile.resolveSibling(
                        replaceExtension(moduleFile.getFileName().toString(), ".ecore"));
                report.recordEvent("Compiling module " + moduleFile.getFileName() + " -> "
                        + generatedModuleEcore.getFileName());
                try {
                    compileModule(moduleFile, generatedModuleEcore, report);
                } catch (IOException ex) {
                    failedInLastPass.add(moduleFile + " -> " + ex.getMessage());
                    report.recordEvent(
                            "Module compilation deferred for " + moduleFile.getFileName() + ": "
                                    + ex.getMessage());
                }
            }

            if (failedInLastPass.isEmpty()) {
                return;
            }
        }

        throw new IOException(
                "Some modules could not be compiled after repeated passes: " + failedInLastPass);
    }

    private void compileModule(Path input, Path output, ConversionReport report)
            throws IOException {
        try {
            EmfaticCompiler.CompilationResult result = emfaticCompiler.compile(input);
            result.diagnostics().forEach(report::addDiagnostic);
            if (result.hasErrors()) {
                throw new IOException(buildHumanReadableFailure(input, result.diagnostics()));
            }
            if (result.resource().getContents().isEmpty()) {
                throw new IOException(
                        "No Ecore content was produced while compiling " + input.getFileName());
            }
            saveAsEcore(output, result.resource().getContents(), report);
        } catch (RuntimeException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    private void saveAsEcore(Path output, List<EObject> contents, ConversionReport report)
            throws IOException {
        Path absoluteOutput = output.toAbsolutePath();
        if (absoluteOutput.getParent() != null) {
            Files.createDirectories(absoluteOutput.getParent());
        }
        ResourceSet resourceSet = resourceSupport.newResourceSet();
        Resource outputResource = resourceSupport.newEcoreResource(resourceSet,
                URI.createFileURI(absoluteOutput.toString()));
        List<EObject> normalizedContents = ecoreCombiner.copy(contents);
        ecoreModelNormalizer.normalize(normalizedContents);
        outputResource.getContents().addAll(normalizedContents);
        outputResource.save(null);
        diagnosticMapper.map(outputResource).forEach(report::addDiagnostic);
    }

    private void materializeModularSingleFileOutputs(
            Path tempDirectory,
            List<ModuleDescriptor> modules,
            Path requestedInput,
            Path requestedOutput,
            ConversionReport report) throws IOException {
        Path absoluteOutput = requestedOutput.toAbsolutePath();
        Path outputDirectory = absoluteOutput.getParent();
        if (outputDirectory == null) {
            throw new IOException(
                    "The output path must have a parent directory for modular single-file conversion.");
        }
        Files.createDirectories(outputDirectory);

        ResourceSet resourceSet = resourceSupport.newResourceSet();
        loadCompiledResources(
                resourceSet,
                tempDirectory,
                tempDirectory.resolve(
                        replaceExtension(requestedInput.getFileName().toString(), ".ecore")));

        Map<URI, URI> uriRemapping = new HashMap<>();

        for (ModuleDescriptor module : modules) {
            Path tempEcore = module.sourceFile().resolveSibling(
                    replaceExtension(module.sourceFile().getFileName().toString(), ".ecore"));
            Resource resource = resourceSet.getResource(URI.createFileURI(tempEcore.toString()),
                    true);
            URI originalUri = resource.getURI();
            Path finalOutput = module.sourceFile().getFileName().toString()
                    .equals(requestedInput.getFileName().toString())
                    ? absoluteOutput
                    : outputDirectory.resolve(
                            replaceExtension(module.sourceFile().getFileName().toString(),
                                    ".ecore"));
            URI finalUri = URI.createFileURI(finalOutput.toString());
            resource.setURI(finalUri);
            uriRemapping.put(originalUri, finalUri);
        }

        resourceSet.getURIConverter().getURIMap().putAll(uriRemapping);
        EcoreUtil.resolveAll(resourceSet);

        for (Resource resource : resourceSet.getResources()) {
            ecoreModelNormalizer.normalize(resource.getContents());
            resource.save(null);
            Path outputPath = Path.of(resource.getURI().toFileString());
            report.recordEvent("Wrote modular dependency output: " + outputPath.getFileName());
        }
    }

    private WorkspacePlan planWorkspace(Collection<Path> seedFiles) throws IOException {
        Set<Path> modules = new LinkedHashSet<>();
        Set<Path> externalEcores = new LinkedHashSet<>();
        ArrayList<Path> queue = new ArrayList<>();

        for (Path seedFile : seedFiles) {
            Path normalizedSeed = seedFile.toAbsolutePath().normalize();
            modules.add(normalizedSeed);
            queue.add(normalizedSeed);
        }

        for (int index = 0; index < queue.size(); index++) {
            Path current = queue.get(index);
            ModuleDescriptor descriptor = importScanner.scan(current);
            for (String importedEcore : descriptor.importedEcoreFiles()) {
                Path importedEcorePath = current.getParent().resolve(importedEcore).normalize();
                Optional<Path> importedModule = resolveImportedModule(importedEcorePath);
                if (importedModule.isPresent()) {
                    Path normalizedModule = importedModule.get().toAbsolutePath().normalize();
                    if (modules.add(normalizedModule)) {
                        queue.add(normalizedModule);
                    }
                } else if (Files.exists(importedEcorePath)) {
                    externalEcores.add(importedEcorePath.toAbsolutePath().normalize());
                }
            }
        }

        Set<Path> allInputs = new LinkedHashSet<>(modules);
        allInputs.addAll(externalEcores);
        Path workspaceRoot = commonAncestor(allInputs);
        return new WorkspacePlan(workspaceRoot, List.copyOf(modules), List.copyOf(externalEcores));
    }

    private Optional<Path> resolveImportedModule(Path importedEcorePath) {
        String fileName = importedEcorePath.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".ecore")) {
            return Optional.empty();
        }
        String baseName = fileName.substring(0, fileName.length() - ".ecore".length());
        Path parent = importedEcorePath.getParent();
        if (parent == null) {
            return Optional.empty();
        }
        Path emfPath = parent.resolve(baseName + ".emf");
        if (Files.exists(emfPath)) {
            return Optional.of(emfPath);
        }
        Path emfaticPath = parent.resolve(baseName + ".emfatic");
        if (Files.exists(emfaticPath)) {
            return Optional.of(emfaticPath);
        }
        return Optional.empty();
    }

    private Path copyIntoWorkspace(Path source, Path workspaceRoot, Path tempDirectory,
            ConversionReport report) {
        Path relativePath = workspaceRoot.relativize(source.toAbsolutePath().normalize());
        Path target = tempDirectory.resolve(relativePath.toString());
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            report.recordEvent("Copied dependency into temp workspace: " + relativePath);
            return target;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to mirror workspace dependency " + source, ex);
        }
    }

    private Path commonAncestor(Collection<Path> paths) {
        if (paths.isEmpty()) {
            throw new IllegalStateException("No workspace paths were provided.");
        }
        var iterator = paths.iterator();
        Path ancestor = iterator.next().toAbsolutePath().normalize().getParent();
        if (ancestor == null) {
            throw new IllegalStateException("Could not determine a common workspace root.");
        }
        while (iterator.hasNext()) {
            ancestor = commonAncestor(ancestor, iterator.next().toAbsolutePath().normalize());
        }
        return ancestor;
    }

    private Path commonAncestor(Path leftAncestor, Path rightPath) {
        Path candidate = leftAncestor;
        Path normalizedRight = rightPath.toAbsolutePath().normalize();
        while (candidate != null && !normalizedRight.startsWith(candidate)) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("Could not determine a common workspace root.");
        }
        return candidate;
    }

    private List<Resource> loadCompiledResources(ResourceSet resourceSet, Path tempDirectory,
            Path generatedRootEcore) throws IOException {
        List<Path> ecoreFiles;
        try (Stream<Path> stream = Files.walk(tempDirectory)) {
            ecoreFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                            .endsWith(".ecore"))
                    .sorted(Comparator.comparing(path -> path.equals(generatedRootEcore) ? ""
                            : path.getFileName().toString()))
                    .toList();
        }

        List<Resource> resources = new ArrayList<>();
        for (Path ecoreFile : ecoreFiles) {
            resources.add(resourceSet.getResource(URI.createFileURI(ecoreFile.toString()), true));
        }
        return resources;
    }

    private String buildHumanReadableFailure(Path input, List<DiagnosticEntry> diagnostics) {
        String details = diagnostics.stream()
                .filter(diagnostic -> diagnostic.severity() == DiagnosticEntry.Severity.ERROR)
                .map(diagnostic -> {
                    StringBuilder builder = new StringBuilder();
                    builder.append(diagnostic.message());
                    if (diagnostic.line() > 0) {
                        builder.append(" (line ").append(diagnostic.line());
                        if (diagnostic.column() > 0) {
                            builder.append(", column ").append(diagnostic.column());
                        }
                        builder.append(')');
                    }
                    if (!diagnostic.hint().isBlank()) {
                        builder.append(". ").append(diagnostic.hint());
                    }
                    return builder.toString();
                })
                .distinct()
                .reduce((left, right) -> left + " | " + right)
                .orElse("The Emfatic parser reported an unspecified error.");
        return "Failed to compile " + input.getFileName() + ": " + details;
    }

    private void generateStubEcores(List<ModuleDescriptor> modules, ConversionReport report)
            throws IOException {
        for (ModuleDescriptor module : modules) {
            StubMetamodelDefinition definition = stubMetamodelExtractor.extract(
                    module.sourceFile());
            Path stubOutput = module.sourceFile().resolveSibling(
                    replaceExtension(module.sourceFile().getFileName().toString(), ".ecore"));
            stubEcoreGenerator.generate(definition, stubOutput);
            report.recordEvent("Generated bootstrap stub Ecore: " + stubOutput.getFileName());
        }
    }

    private void validateRequest(Path input, Path output, boolean overwrite,
            ConversionReport report) {
        if (Files.notExists(input)) {
            report.fail(
                    "The input path does not exist.",
                    "Provide an existing .emf/.emfatic file or a directory containing Emfatic modules.",
                    null);
            throw new ConversionException("Input path does not exist", report, true, null);
        }
        if (Files.exists(output) && !overwrite) {
            report.fail(
                    "The output file already exists.",
                    "Use --overwrite to replace the existing file or provide a different --output path.",
                    null);
            throw new ConversionException("Output already exists", report, true, null);
        }
        if (Files.isRegularFile(input) && !isEmfaticFile(input)) {
            report.fail(
                    "The input file is not an Emfatic file.",
                    "Use a .emf or .emfatic file as input.",
                    null);
            throw new ConversionException("Unsupported input file", report, true, null);
        }
    }

    private boolean isEmfaticFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".emf") || fileName.endsWith(".emfatic");
    }

    private String replaceExtension(String fileName, String newExtension) {
        int extensionStart = fileName.lastIndexOf('.');
        String baseName = extensionStart < 0 ? fileName : fileName.substring(0, extensionStart);
        return baseName + newExtension;
    }

    private record WorkspacePlan(Path workspaceRoot, List<Path> moduleFiles,
                                 List<Path> externalEcoreFiles) {

    }

    private record PreparedWorkspace(
            Path tempDirectory,
            Map<Path, Path> stagedPaths,
            List<ModuleDescriptor> modules) {

        private Path requireStagedPath(Path originalPath) {
            Path normalizedOriginal = originalPath.toAbsolutePath().normalize();
            Path stagedPath = stagedPaths.get(normalizedOriginal);
            if (stagedPath == null) {
                throw new IllegalStateException(
                        "No staged workspace path exists for " + normalizedOriginal);
            }
            return stagedPath;
        }
    }
}
