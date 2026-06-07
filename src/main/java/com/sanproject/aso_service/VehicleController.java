package com.sanproject.aso_service;

import com.sanproject.aso_service.catalog.VehicleCatalog;
import com.sanproject.aso_service.catalog.VehicleCatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Vehicle CRUD scoped to customers; catalog endpoint drives the frontend configurator.
@RestController
@RequestMapping("/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;
    private final VehicleCatalogService catalogService;

    public VehicleController(VehicleService vehicleService, VehicleCatalogService catalogService) {
        this.vehicleService = vehicleService;
        this.catalogService = catalogService;
    }

    @GetMapping("/catalog")
    public VehicleCatalog getCatalog() {
        return catalogService.getCatalog();
    }

    @GetMapping
    public List<Vehicle> getAllVehicles() {
        return vehicleService.getAllVehicles();
    }

    @GetMapping("/customer/{customerId}")
    public List<Vehicle> getVehiclesByCustomerId(@PathVariable Long customerId) {
        return vehicleService.getVehiclesByCustomerId(customerId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Vehicle> getVehicleById(@PathVariable Long id) {
        Vehicle vehicle = vehicleService.getVehicleById(id);

        if (vehicle == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(vehicle);
    }

    @PostMapping("/customer/{customerId}")
    public ResponseEntity<Vehicle> createVehicle(
            @PathVariable Long customerId,
            @Valid @RequestBody CreateVehicleRequest request) {

        Vehicle createdVehicle = vehicleService.createVehicle(customerId, request);

        if (createdVehicle == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(createdVehicle);
    }

    @DeleteMapping("/{id}/customer/{customerId}")
    public ResponseEntity<Void> removeVehicle(
            @PathVariable Long id,
            @PathVariable Long customerId) {

        boolean removed = vehicleService.removeVehicleFromCustomer(customerId, id);

        if (!removed) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }
}
