package io.mehdieidi.modless.mde.etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class EpsilonEtlExecutorTest {

    @Test
    void reportsMissingModuleAsValidationDiagnostic() {
        EpsilonEtlExecutor executor = new EpsilonEtlExecutor();
        EtlExecutionRequest request = new EtlExecutionRequest(
                Path.of("missing.etl"),
                Path.of("."),
                List.of(),
                false,
                true);

        EtlExecutionException exception = assertThrows(EtlExecutionException.class,
                () -> executor.execute(request));

        assertEquals(EtlExecutionStatus.FAILED, exception.getReport().status());
        assertTrue(exception.getReport().diagnostics().stream()
                .anyMatch(d -> d.phase() == ExecutionPhase.VALIDATION
                        && d.reason().contains("ETL module does not exist")));
    }
}
