package com.sanproject.aso_service.catalog;

import java.util.List;

// One Aston Martin model family (e.g. DBX); availableYears is computed at catalog load.
public class ModelLine {

    private String id;
    private String name;
    private String era;
    private List<Integer> availableYears;
    private List<BodyStyleOption> bodyStyles;

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

    public String getEra() {
        return era;
    }

    public void setEra(String era) {
        this.era = era;
    }

    public List<Integer> getAvailableYears() {
        return availableYears;
    }

    public void setAvailableYears(List<Integer> availableYears) {
        this.availableYears = availableYears;
    }

    public List<BodyStyleOption> getBodyStyles() {
        return bodyStyles;
    }

    public void setBodyStyles(List<BodyStyleOption> bodyStyles) {
        this.bodyStyles = bodyStyles;
    }
}
