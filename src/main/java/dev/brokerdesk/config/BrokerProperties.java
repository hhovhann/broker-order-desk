package dev.brokerdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** All business parameters live here, never in code. Synthetic defaults. */
@ConfigurationProperties("broker")
public record BrokerProperties(int bandPercent, long maxOrderValueMills, int maintenanceMarginPercent, String demoPassword) {}
