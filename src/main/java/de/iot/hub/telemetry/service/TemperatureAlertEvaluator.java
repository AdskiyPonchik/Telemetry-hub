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

    public boolean isAlertActive(int consecutiveBreaches) {
        return consecutiveBreaches >= REQUIRED_BREACHES;
    }

}