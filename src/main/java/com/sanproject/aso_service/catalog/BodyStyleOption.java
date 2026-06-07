package com.sanproject.aso_service.catalog;

import java.util.List;

// Body variant within a model line (e.g. Coupe, Volante).
public class BodyStyleOption {

    private String id;
    private String name;
    private List<ConfigurationOption> configurations;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ConfigurationOption> getConfigurations() {
        return configurations;
    }

    public void setConfigurations(List<ConfigurationOption> configurations) {
        this.configurations = configurations;
    }
}
