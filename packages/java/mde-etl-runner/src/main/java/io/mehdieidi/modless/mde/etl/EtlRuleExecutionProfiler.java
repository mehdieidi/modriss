package io.mehdieidi.modless.mde.etl;

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

final class EtlRuleExecutionProfiler implements IExecutionListener {

    private final Map<ModuleElement, ArrayDeque<Long>> starts = new IdentityHashMap<>();
    private final Map<String, RuleTiming> timings = new LinkedHashMap<>();

    static EtlRuleExecutionProfiler createIfEnabled() {
        return Boolean.getBoolean("modless.etlRuleProfiler")
                ? new EtlRuleExecutionProfiler() : null;
    }

    @Override
    public void aboutToExecute(ModuleElement element, IEolContext context) {
        if (label(element) != null) {
            starts.computeIfAbsent(element, ignored -> new ArrayDeque<>())
                    .push(System.nanoTime());
        }
    }

    @Override
    public void finishedExecuting(ModuleElement element, Object result, IEolContext context) {
        finish(element);
    }

    @Override
    public void finishedExecutingWithException(
            ModuleElement element, EolRuntimeException exception, IEolContext context) {
        finish(element);
    }

    void printReport() {
        timings.entrySet().stream()
                .sorted(Map.Entry.<String, RuleTiming>comparingByValue(
                        Comparator.comparingLong(RuleTiming::totalNanos)).reversed())
                .forEach(entry -> System.out.printf(
                        "ETL_RULE_PROFILE %s %dms count=%d%n",
                        entry.getKey(),
                        entry.getValue().totalNanos() / 1_000_000,
                        entry.getValue().count()));
    }

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

    private static final class RuleTiming {

        private long totalNanos;
        private long count;

        void add(long nanos) {
            totalNanos += nanos;
            count++;
        }

        long totalNanos() {
            return totalNanos;
        }

        long count() {
            return count;
        }
    }
}
