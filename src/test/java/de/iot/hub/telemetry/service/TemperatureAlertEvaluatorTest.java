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
}
