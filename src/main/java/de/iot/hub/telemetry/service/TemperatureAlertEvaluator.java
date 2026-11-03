package de.iot.hub.telemetry.service;

import org.springframework.stereotype.Component;

@Component
public class TemperatureAlertEvaluator {

    public static final int REQUIRED_BREACHES = 3;

    public int evaluate(double temperature,
                        double maxTemperature,
                        int previousBreaches) {
        if (temperature > maxTemperature) {
            return Math.min(previousBreaches, REQUIRED_BREACHES - 1) + 1; // counter stops at 3
        }
        return 0;
    }

    /** Pure state transition; only accepted, unique readings reach this method. */
    public LifecycleResult evaluateLifecycle(double temperature, double maxTemperature,
            int previousBreaches, int previousNormalReadings, boolean incidentActive) {
        boolean hot = temperature > maxTemperature;
        if (incidentActive) {
            int normal = hot ? 0 : Math.min(previousNormalReadings, REQUIRED_BREACHES - 1) + 1;
            if (normal == REQUIRED_BREACHES) {
                return new LifecycleResult(0, 0, false, true);
            }
            int breaches = hot ? evaluate(temperature, maxTemperature, previousBreaches) : 0;
            return new LifecycleResult(breaches, normal, false, false);
        }
        int breaches = evaluate(temperature, maxTemperature, previousBreaches);
        return new LifecycleResult(breaches, 0, breaches == REQUIRED_BREACHES, false);
    }

    public record LifecycleResult(int consecutiveBreaches, int consecutiveNormalReadings,
                                  boolean openIncident, boolean recoverIncident) {
    }

    public boolean isAlertActive(int consecutiveBreaches) {
        return consecutiveBreaches >= REQUIRED_BREACHES;
    }

}