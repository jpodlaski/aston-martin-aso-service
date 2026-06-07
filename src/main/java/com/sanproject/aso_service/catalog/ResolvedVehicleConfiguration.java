package com.sanproject.aso_service.catalog;

// Immutable snapshot of all catalog fields for one configurationId.
public class ResolvedVehicleConfiguration {

    private final String configurationId;
    private final String modelLine;
    private final String model;
    private final String productionEra;
    private final String bodyStyle;
    private final String engine;
    private final String power;
    private final String transmission;
    private final String drivetrain;

    public ResolvedVehicleConfiguration(
            String configurationId,
            String modelLine,
            String model,
            String productionEra,
            String bodyStyle,
            String engine,
            String power,
            String transmission,
            String drivetrain) {
        this.configurationId = configurationId;
        this.modelLine = modelLine;
        this.model = model;
        this.productionEra = productionEra;
        this.bodyStyle = bodyStyle;
        this.engine = engine;
        this.power = power;
        this.transmission = transmission;
        this.drivetrain = drivetrain;
    }

    public String getConfigurationId() {
        return configurationId;
    }

    public String getModelLine() {
        return modelLine;
    }

    public String getModel() {
        return model;
    }

    public String getProductionEra() {
        return productionEra;
    }

    public String getBodyStyle() {
        return bodyStyle;
    }

    public String getEngine() {
        return engine;
    }

    public String getPower() {
        return power;
    }

    public String getTransmission() {
        return transmission;
    }

    public String getDrivetrain() {
        return drivetrain;
    }
}
