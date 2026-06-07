package com.sanproject.aso_service.catalog;

import java.util.List;

// Root JSON structure deserialized from aston-martin-catalog.json.
public class VehicleCatalog {

    private List<ModelLine> modelLines;

    public List<ModelLine> getModelLines() {
        return modelLines;
    }

    public void setModelLines(List<ModelLine> modelLines) {
        this.modelLines = modelLines;
    }
}
