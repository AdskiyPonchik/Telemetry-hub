package de.iot.hub.telemetry.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemperatureAlertEvaluatorTest {
    private final TemperatureAlertEvaluator evaluator = new TemperatureAlertEvaluator();

    @Test
    void interruptedSequenceResetsAndSustainedOverheatingActivatesAlert() {
        double[] temperatures = {81, 79, 82, 84, 85, 86, 80};
        int[] expectedCounts = {1, 0, 1, 2, 3, 3, 0};
        int count = 0;

        for (int i = 0; i < temperatures.length; i++) {
            count = evaluator.evaluate(temperatures[i], 80, count);
            assertEquals(expectedCounts[i], count, "Reading " + i);
            assertEquals(expectedCounts[i] >= 3, evaluator.isAlertActive(count), "Reading " + i);
        }
    }

    @ParameterizedTest
    @CsvSource({"99, 100, 2, 0", "100, 100, 2, 0", "100.1, 100, 2, 3",
            "81, 80, 0, 1", "81, 80, 1, 2", "81, 80, 3, 3",
            "81, 80, 2147483647, 3"})
    void usesSuppliedThresholdAndCapsCounter(double temperature, double threshold, int previous, int expected) {
        assertEquals(expected, evaluator.evaluate(temperature, threshold, previous));
    }

    @ParameterizedTest
    @CsvSource({"0, false", "1, false", "2, false", "3, true", "4, true"})
    void alertRequiresAtLeastThreeBreaches(int count, boolean active) {
        assertEquals(active, evaluator.isAlertActive(count));
    }

    @Test
    void lifecycleRequiresThreeUninterruptedBreachesToOpenAndThreeNormalReadingsToRecover() {
        double[] temperatures = {81, 80, 82, 83, 84, 85, 80, 79, 81, 80, 79, 78, 81, 82, 83};
        int[] expectedBreaches = {1, 0, 1, 2, 3, 3, 0, 0, 1, 0, 0, 0, 1, 2, 3};
        int[] expectedNormal = {0, 0, 0, 0, 0, 0, 1, 2, 0, 1, 2, 0, 0, 0, 0};
        int breaches = 0;
        int normal = 0;
        boolean active = false;

        for (int i = 0; i < temperatures.length; i++) {
            var result = evaluator.evaluateLifecycle(temperatures[i], 80, breaches, normal, active);
            assertEquals(expectedBreaches[i], result.consecutiveBreaches(), "breaches at reading " + i);
            assertEquals(expectedNormal[i], result.consecutiveNormalReadings(), "normal at reading " + i);
            assertEquals(i == 4 || i == 14, result.openIncident(), "open at reading " + i);
            assertEquals(i == 11, result.recoverIncident(), "recover at reading " + i);
            if (result.openIncident()) active = true;
            if (result.recoverIncident()) active = false;
            breaches = result.consecutiveBreaches();
            normal = result.consecutiveNormalReadings();
        }
    }

    @Test
    void activeIncidentDoesNotReopenDuringSustainedOverheating() {
        var result = evaluator.evaluateLifecycle(81, 80, Integer.MAX_VALUE, 2, true);

        assertEquals(3, result.consecutiveBreaches());
        assertEquals(0, result.consecutiveNormalReadings());
        assertEquals(false, result.openIncident());
        assertEquals(false, result.recoverIncident());
    }

    @Test
    void thresholdEqualityRecoversAndResetsCountersWithoutOverflow() {
        var result = evaluator.evaluateLifecycle(80, 80, 3, Integer.MAX_VALUE, true);

        assertEquals(0, result.consecutiveBreaches());
        assertEquals(0, result.consecutiveNormalReadings());
        assertEquals(false, result.openIncident());
        assertEquals(true, result.recoverIncident());
    }
}
