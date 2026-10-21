package io.mehdieidi.modriss.platform.psm;

import io.mehdieidi.modriss.mde.emf.DateAwareXmiResource;
import io.mehdieidi.modriss.mde.etl.EpsilonEtlExecutor;
import io.mehdieidi.modriss.mde.etl.EtlExecutionException;
import io.mehdieidi.modriss.mde.etl.EtlExecutionRequest;
import io.mehdieidi.modriss.mde.etl.EtlExecutionStatus;
import io.mehdieidi.modriss.mde.etl.EtlModelConfiguration;
import io.mehdieidi.modriss.mde.etl.PimToAwsPsmDefaults;
import io.mehdieidi.modriss.mde.generation.EgxGenerationException;
import io.mehdieidi.modriss.mde.generation.EgxGenerationRequest;
import io.mehdieidi.modriss.mde.generation.EpsilonEgxGenerator;
import io.mehdieidi.modriss.mde.generation.GenerationModelConfiguration;
import io.mehdieidi.modriss.mde.generation.GenerationStatus;
import io.mehdieidi.modriss.mde.validation.EpsilonEvlValidator;
import io.mehdieidi.modriss.mde.validation.EvlValidationException;
import io.mehdieidi.modriss.mde.validation.EvlValidationRequest;
import io.mehdieidi.modriss.mde.validation.EvlValidationStatus;
import io.mehdieidi.modriss.mde.validation.FileEvlModelConfiguration;
import io.mehdieidi.modriss.mde.validation.ValidationSeverity;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.epsilon.egl.EglModule;
import org.eclipse.epsilon.egl.EgxModule;
import org.eclipse.epsilon.etl.EtlModule;
import org.eclipse.epsilon.evl.EvlModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * Entry point for the networkless, disposable PSM execution container. Never run in the web JVM.
 */
public final class PsmPackageWorker {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Duration TIMEOUT = Duration.ofMinutes(2);

  private PsmPackageWorker() {}

  public static void main(String[] args) throws Exception {
    var protocolOutput = System.out;
    System.setOut(System.err);
    PsmWorkerResult result;
    try {
      byte[] input = System.in.readNBytes(100 * 1024 * 1024 + 1);
      if (input.length > 100 * 1024 * 1024)
        throw new IllegalArgumentException("Input exceeds 100 MB.");
      result = execute(MAPPER.readValue(input, PsmWorkerRequest.class));
    } catch (Exception ex) {
      result =
          new PsmWorkerResult(
              false,
              null,
              null,
              List.of(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()),
              Map.of());
    }
    protocolOutput.write(MAPPER.writeValueAsBytes(result));
  }

  /** Public for tests that execute trusted repository fixtures. Production uses a container. */
  public static PsmWorkerResult execute(PsmWorkerRequest request) throws Exception {
    Path work = Files.createTempDirectory("psm-package-").toAbsolutePath();
    try {
      return execute(request, work);
    } catch (Exception ex) {
      List<String> issues = new ArrayList<>();
      if (ex instanceof EtlExecutionException etl) {
        etl.getReport().diagnostics().forEach(d -> issues.add(d.toString()));
      } else if (ex instanceof EgxGenerationException egx) {
        egx.getReport().diagnostics().forEach(d -> issues.add(d.toString()));
      } else if (ex instanceof EvlValidationException evl) {
        evl.getReport().diagnostics().forEach(d -> issues.add(d.toString()));
      }
      if (issues.isEmpty())
        issues.add(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
      return new PsmWorkerResult(false, null, null, issues, Map.of());
    } finally {
      try (var paths = Files.walk(work)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(path);
      }
    }
  }

  private static PsmWorkerResult execute(PsmWorkerRequest request, Path work) throws Exception {
    PsmPlatformPackage platform = request.platform();
    Path ecore = work.resolve("platform.ecore");
    requireXml(platform.ecore());
    Files.write(ecore, platform.ecore());
    Path etlRoot = work.resolve("etl");
    Path generationRoot = work.resolve("generation");
    Path evlRoot = work.resolve("validation");
    if (platform.transformationZip() != null)
      PsmPackageArchive.extract(platform.transformationZip(), etlRoot, Set.of("etl", "eol"));
    if (platform.generationZip() != null)
      PsmPackageArchive.extract(
          platform.generationZip(), generationRoot, Set.of("egx", "egl", "eol"));
    if (platform.validationZip() != null) {
      PsmPackageArchive.extract(platform.validationZip(), evlRoot, Set.of("evl", "eol"));
    }
    var resources = new ResourceSetImpl();
    resources
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("ecore", new EcoreResourceFactoryImpl());
    resources
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("xmi", (Resource.Factory) DateAwareXmiResource::new);
    resources.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
    Resource metamodel = resources.getResource(URI.createFileURI(ecore.toString()), true);
    EcoreUtil.resolveAll(resources);
    if (resources.getResources().stream().anyMatch(resource -> resource != metamodel)) {
      throw new IllegalArgumentException(
          "Ecore must be self-contained; include EPackages in the uploaded file.");
    }
    if (!EcoreUtil.UnresolvedProxyCrossReferencer.find(metamodel).isEmpty()) {
      throw new IllegalArgumentException("Ecore contains unresolved type references.");
    }
    List<EPackage> packages = new ArrayList<>();
    for (EObject object : metamodel.getContents()) {
      if (!(object instanceof EPackage pkg))
        throw new IllegalArgumentException("Ecore must contain EPackages.");
      collectPackages(pkg, packages);
    }
    if (packages.isEmpty()) throw new IllegalArgumentException("Ecore contains no EPackage.");
    List<String> issues = new ArrayList<>();
    for (EPackage pkg : packages) {
      if (pkg.getNsURI() == null || pkg.getNsURI().isBlank())
        throw new IllegalArgumentException("Every EPackage needs a namespace URI.");
      resources.getPackageRegistry().put(pkg.getNsURI(), pkg);
      diagnostics(Diagnostician.INSTANCE.validate(pkg), issues);
    }
    if (!issues.isEmpty()) return new PsmWorkerResult(false, null, null, issues, Map.of());
    var codec = new PsmModelCodec(packages);
    ArrayNode schema = MAPPER.createArrayNode();
    for (EPackage pkg : packages) {
      for (EClassifier classifier : pkg.getEClassifiers()) {
        if (!(classifier instanceof EClass type)) continue;
        var cls =
            schema
                .addObject()
                .put("name", type.getName())
                .put("namespace", pkg.getNsURI())
                .put("typeKey", codec.typeKey(type))
                .put("abstract", type.isAbstract() || type.isInterface());
        var supertypes = cls.putArray("supertypes");
        type.getEAllSuperTypes()
            .forEach(
                parent -> supertypes.add(parent.getEPackage().getNsURI() + "#" + parent.getName()));
        ArrayNode features = cls.putArray("features");
        for (EStructuralFeature feature : type.getEAllStructuralFeatures()) {
          var f =
              features
                  .addObject()
                  .put("name", feature.getName())
                  .put("type", feature.getEType().getName())
                  .put("fieldName", PsmModelCodec.featureKey(feature))
                  .put("required", feature.getLowerBound() > 0)
                  .put("many", feature.isMany())
                  .put("typeNamespace", feature.getEType().getEPackage().getNsURI())
                  .put("kind", feature instanceof EAttribute ? "attribute" : "reference")
                  .put("lowerBound", feature.getLowerBound())
                  .put("upperBound", feature.getUpperBound())
                  .put("changeable", feature.isChangeable())
                  .put("derived", feature.isDerived())
                  .put("defaultValue", feature.getDefaultValueLiteral())
                  .put("containment", feature instanceof EReference ref && ref.isContainment());
          if (feature.getEType() instanceof EEnum enumeration) {
            var literals = f.putArray("literals");
            enumeration.getELiterals().forEach(literal -> literals.add(literal.getLiteral()));
          }
        }
      }
    }
    if (request.operation().equals("inspect")) {
      // Parse all supplied scripts, including helpers, before accepting an immutable package.
      try (var paths = Files.walk(work)) {
        for (Path path : paths.filter(Files::isRegularFile).toList()) {
          String name = path.getFileName().toString();
          org.eclipse.epsilon.common.module.IModule module =
              name.endsWith(".etl")
                  ? new EtlModule()
                  : name.endsWith(".egx")
                      ? new EgxModule()
                      : name.endsWith(".egl")
                          ? new EglModule()
                          : name.endsWith(".evl")
                              ? new EvlModule()
                              : name.endsWith(".eol")
                                  ? new org.eclipse.epsilon.eol.EolModule()
                                  : null;
          if (module == null) continue;
          module.parse(path.toFile());
          module
              .getParseProblems()
              .forEach(problem -> issues.add(work.relativize(path) + ": " + problem));
        }
      }
      return new PsmWorkerResult(issues.isEmpty(), null, schema, issues, Map.of());
    }
    Path modelFile = work.resolve("model.xmi");
    if (request.operation().equals("transform")) {
      Path source = work.resolve("pim.xmi");
      Path pimEcore = work.resolve("pim.ecore");
      Files.write(source, request.sourceXmi());
      Files.write(pimEcore, request.pimEcore());
      var report =
          new EpsilonEtlExecutor(TIMEOUT, 65536)
              .execute(
                  new EtlExecutionRequest(
                      etlRoot.resolve(platform.transformationEntry()),
                      etlRoot,
                      List.of(
                          EtlModelConfiguration.source(
                              "PIM", PimToAwsPsmDefaults.SOURCE_ALIASES, source, List.of(pimEcore)),
                          EtlModelConfiguration.target(
                              "PSM", aliases(packages), modelFile, List.of(ecore), false)),
                      true,
                      true));
      if (report.status() != EtlExecutionStatus.SUCCEEDED)
        throw new IllegalArgumentException("ETL transformation failed: " + report.diagnostics());
    } else if (request.operation().equals("write")) {
      Resource output = resources.createResource(URI.createFileURI(modelFile.toString()));
      codec.write(output, request.modelJson());
      output.save(Map.of(XMLResource.OPTION_ENCODING, "UTF-8"));
    } else {
      requireXml(request.sourceXmi());
      Files.write(modelFile, request.sourceXmi());
    }
    if (Files.size(modelFile) > 20L * 1024 * 1024)
      throw new IllegalArgumentException("PSM exceeds 20 MB.");
    requireXml(Files.readAllBytes(modelFile));
    Resource model = resources.getResource(URI.createFileURI(modelFile.toString()), true);
    EcoreUtil.resolveAll(resources);
    if (model.getContents().isEmpty()) issues.add("Transformation produced an empty model.");
    if (!EcoreUtil.UnresolvedProxyCrossReferencer.find(model).isEmpty())
      issues.add("Model contains unresolved references.");
    model.getErrors().forEach(error -> issues.add(error.getMessage()));
    for (EObject root : model.getContents()) {
      if (!packages.contains(root.eClass().getEPackage()))
        issues.add("Model root does not belong to this PSM metamodel.");
      diagnostics(Diagnostician.INSTANCE.validate(root), issues);
    }
    if (!issues.isEmpty()) return new PsmWorkerResult(false, null, schema, issues, Map.of());
    if (request.operation().equals("validate") && platform.validationZip() != null) {
      var report =
          new EpsilonEvlValidator(TIMEOUT, 65536)
              .validate(
                  EvlValidationRequest.forRoot(
                      evlRoot,
                      List.of(
                          new FileEvlModelConfiguration(
                              "PSM", aliases(packages), modelFile, List.of(ecore), true)),
                      true));
      report
          .violations()
          .forEach(
              violation -> issues.add(violation.constraintName() + ": " + violation.message()));
      report.diagnostics().stream()
          .filter(d -> d.severity() == ValidationSeverity.ERROR)
          .forEach(d -> issues.add(d.toString()));
      if (report.status() != EvlValidationStatus.SUCCEEDED && issues.isEmpty())
        issues.add("EVL execution failed.");
      return new PsmWorkerResult(
          !report.hasMandatoryViolations() && report.status() == EvlValidationStatus.SUCCEEDED,
          null,
          schema,
          issues,
          Map.of());
    }
    Map<String, String> files = new LinkedHashMap<>();
    if (request.operation().equals("generate")) {
      Path output = work.resolve("output");
      var report =
          new EpsilonEgxGenerator(TIMEOUT, 65536)
              .generate(
                  new EgxGenerationRequest(
                      generationRoot.resolve(platform.generationEntry()),
                      generationRoot,
                      output,
                      List.of(
                          new GenerationModelConfiguration(
                              "PSM", aliases(packages), modelFile, List.of(ecore), false)),
                      true,
                      true));
      if (report.status() != GenerationStatus.SUCCEEDED)
        throw new IllegalArgumentException("EGX generation failed: " + report.diagnostics());
      long total = 0;
      try (var paths = Files.walk(output)) {
        for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
          if (Files.isSymbolicLink(path) || !path.toRealPath().startsWith(output.toRealPath()))
            throw new IllegalArgumentException("Generated file escapes output directory.");
          long size = Files.size(path);
          total += size;
          if (size > 5L * 1024 * 1024 || total > 50L * 1024 * 1024 || files.size() >= 2000)
            throw new IllegalArgumentException("Generated artifact exceeds size limits.");
          String pathName = output.relativize(path).toString().replace('\\', '/');
          PsmPackageArchive.requirePath(pathName);
          files.put(pathName, Files.readString(path));
        }
      }
      if (files.isEmpty()) throw new IllegalArgumentException("EGX produced no artifacts.");
    }
    var modelJson = codec.read(model);
    ByteArrayOutputStream canonical = new ByteArrayOutputStream();
    model.save(canonical, Map.of(XMLResource.OPTION_ENCODING, "UTF-8"));
    return new PsmWorkerResult(
        true, canonical.toString(StandardCharsets.UTF_8), schema, issues, files, modelJson);
  }

  private static void requireXml(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > 20 * 1024 * 1024)
      throw new IllegalArgumentException("XML must contain between 1 byte and 20 MB.");
    String xml = new String(bytes, StandardCharsets.UTF_8);
    if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY"))
      throw new IllegalArgumentException("XML entities and document types are not supported.");
  }

  private static List<String> aliases(List<EPackage> packages) {
    var names = new java.util.LinkedHashSet<String>();
    for (EPackage pkg : packages) {
      if (pkg.getName() != null) names.add(pkg.getName());
      if (pkg.getNsPrefix() != null) names.add(pkg.getNsPrefix());
    }
    names.remove("PSM");
    return List.copyOf(names);
  }

  private static void collectPackages(EPackage pkg, List<EPackage> result) {
    result.add(pkg);
    pkg.getESubpackages().forEach(child -> collectPackages(child, result));
  }

  private static void diagnostics(Diagnostic diagnostic, List<String> issues) {
    if (diagnostic.getChildren().isEmpty()) {
      if (diagnostic.getSeverity() >= Diagnostic.ERROR) issues.add(diagnostic.getMessage());
    } else diagnostic.getChildren().forEach(child -> diagnostics(child, issues));
  }
}
