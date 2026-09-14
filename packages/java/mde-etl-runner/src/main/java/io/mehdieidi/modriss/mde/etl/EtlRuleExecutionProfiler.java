package io.mehdieidi.modriss.mde.etl;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.epsilon.common.module.ModuleElement;
import org.eclipse.epsilon.eol.dom.ExecutableBlock;
import org.eclipse.epsilon.eol.dom.Operation;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.eclipse.epsilon.eol.execute.context.IEolContext;
import org.eclipse.epsilon.eol.execute.control.IExecutionListener;
import org.eclipse.epsilon.etl.dom.TransformationRule;

/** Optional execution listener that aggregates ETL rule and helper operation runtime totals. */
final class EtlRuleExecutionProfiler implements IExecutionListener {

  /** Start timestamps keyed by AST element identity to support nested execution. */
  private final Map<ModuleElement, ArrayDeque<Long>> starts = new IdentityHashMap<>();

  /** Aggregated timing by rule/block label in first-seen order. */
  private final Map<String, RuleTiming> timings = new LinkedHashMap<>();

  /**
   * Creates the profiler only when the JVM property has explicitly enabled it.
   *
   * @return profiler instance, or {@code null} when disabled
   */
  static EtlRuleExecutionProfiler createIfEnabled() {
    return Boolean.getBoolean("modriss.etlRuleProfiler") ? new EtlRuleExecutionProfiler() : null;
  }

  /**
   * Records the start of executable ETL elements that can be labeled.
   *
   * @param element module element about to execute
   * @param context current Epsilon execution context
   */
  @Override
  public void aboutToExecute(ModuleElement element, IEolContext context) {
    if (label(element) != null) {
      starts.computeIfAbsent(element, ignored -> new ArrayDeque<>()).push(System.nanoTime());
    }
  }

  /**
   * Closes the current timing span for a successfully executed element.
   *
   * @param element executed module element
   * @param result execution result
   * @param context current Epsilon execution context
   */
  @Override
  public void finishedExecuting(ModuleElement element, Object result, IEolContext context) {
    finish(element);
  }

  /**
   * Closes the current timing span for an element that threw an exception.
   *
   * @param element executed module element
   * @param exception runtime exception raised by Epsilon
   * @param context current Epsilon execution context
   */
  @Override
  public void finishedExecutingWithException(
      ModuleElement element, EolRuntimeException exception, IEolContext context) {
    finish(element);
  }

  /** Prints rule timings in descending total runtime order. */
  void printReport() {
    timings.entrySet().stream()
        .sorted(
            Map.Entry.<String, RuleTiming>comparingByValue(
                    Comparator.comparingLong(RuleTiming::totalNanos))
                .reversed())
        .forEach(
            entry ->
                System.out.printf(
                    "ETL_RULE_PROFILE %s %dms count=%d%n",
                    entry.getKey(),
                    entry.getValue().totalNanos() / 1_000_000,
                    entry.getValue().count()));
  }

  /**
   * Adds elapsed time for the current execution span of a labeled element.
   *
   * @param element element that just finished execution
   */
  private void finish(ModuleElement element) {
    String label = label(element);
    if (label == null) {
      return;
    }
    ArrayDeque<Long> elementStarts = starts.get(element);
    if (elementStarts == null || elementStarts.isEmpty()) {
      return;
    }
    long elapsed = System.nanoTime() - elementStarts.pop();
    timings.computeIfAbsent(label, ignored -> new RuleTiming()).add(elapsed);
  }

  /**
   * Derives a stable label for transformation rule blocks and helper operations.
   *
   * @param element Epsilon AST element
   * @return profiling label, or {@code null} when the element is ignored
   */
  private String label(ModuleElement element) {
    if (element instanceof ExecutableBlock<?> block
        && element.getParent() instanceof TransformationRule rule) {
      return rule.getName() + "." + block.getRole();
    }
    if (element.getParent() instanceof ExecutableBlock<?> block
        && block.getParent() instanceof TransformationRule rule) {
      return rule.getName() + "." + block.getRole();
    }
    if (element.getParent() instanceof Operation operation) {
      return "operation." + operation.getName();
    }
    return null;
  }

  /** Aggregates runtime and execution count for a rule label. */
  private static final class RuleTiming {

    /** Total nanoseconds spent in the rule label. */
    private long totalNanos;

    /** Number of completed executions for the rule label. */
    private long count;

    /**
     * Adds one execution sample.
     *
     * @param nanos elapsed nanoseconds
     */
    void add(long nanos) {
      totalNanos += nanos;
      count++;
    }

    /**
     * Returns total runtime.
     *
     * @return elapsed nanoseconds
     */
    long totalNanos() {
      return totalNanos;
    }

    /**
     * Returns execution count.
     *
     * @return number of executions
     */
    long count() {
      return count;
    }
  }
}
