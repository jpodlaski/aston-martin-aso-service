package com.sanproject.aso_service.controller;

import com.sanproject.aso_service.catalog.VehicleCatalog;
import com.sanproject.aso_service.catalog.VehicleCatalogService;
import com.sanproject.aso_service.domain.Vehicle;
import com.sanproject.aso_service.dto.CreateVehicleRequest;
import com.sanproject.aso_service.security.AuthSupport;
import com.sanproject.aso_service.security.AuthUser;
import com.sanproject.aso_service.service.VehicleService;

import com.sanproject.aso_service.catalog.VehicleCatalog;
import com.sanproject.aso_service.catalog.VehicleCatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

// Vehicle CRUD scoped to the authenticated customer; catalog drives the configurator.
@RestController
@RequestMapping("/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;
    private final VehicleCatalogService catalogService;
    private final AuthSupport authSupport;

    public VehicleController(
            VehicleService vehicleService,
            VehicleCatalogService catalogService,
            AuthSupport authSupport) {
        this.vehicleService = vehicleService;
        this.catalogService = catalogService;
        this.authSupport = authSupport;
    }

    @GetMapping("/catalog")
    public VehicleCatalog getCatalog() {
        return catalogService.getCatalog();
    }

    @GetMapping
    public List<Vehicle> getAllVehicles() {
        authSupport.requireCanManageWorkers();
        return vehicleService.getAllVehicles();
    }

    @GetMapping("/me")
    public List<Vehicle> getMyVehicles() {
        AuthUser client = authSupport.requireClient();
        return vehicleService.getVehiclesByCustomerId(client.getId());
    }

    /** Soft-removed vehicles — kept so sold cars still appear in history and booking filters. */
    @GetMapping("/me/history")
    public List<Vehicle> getMyVehicleHistory() {
        AuthUser client = authSupport.requireClient();
        return vehicleService.getVehicleHistoryByCustomerId(client.getId());
    }

    /**
     * Live check while the client types/pastes a VIN in Add vehicle.
     * Does not reveal who owns a taken VIN.
     */
    @GetMapping("/me/vin-available")
    public Map<String, Object> isMyVinAvailable(@RequestParam String vin) {
        authSupport.requireClient();
        boolean available = vehicleService.isVinAvailable(vin);
        return Map.of(
                "vin", VehicleService.normalizeVin(vin),
                "available", available
        );
    }

    @GetMapping("/customer/{customerId}")
    public List<Vehicle> getVehiclesByCustomerId(@PathVariable Long customerId) {
        AuthUser user = authSupport.requireUser();
        if ("CLIENT".equals(user.getRole())) {
            if (!user.getId().equals(customerId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot view another customer's vehicles");
            }
            return vehicleService.getVehiclesByCustomerId(customerId);
        }
        authSupport.requireCanManageWorkers();
        return vehicleService.getVehiclesByCustomerId(customerId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Vehicle> getVehicleById(@PathVariable Long id) {
        AuthUser user = authSupport.requireUser();
        Vehicle vehicle = vehicleService.getVehicleById(id);

        if (vehicle == null || vehicle.isRemovedFromAccount()) {
            return ResponseEntity.notFound().build();
        }

        if ("CLIENT".equals(user.getRole())) {
            if (vehicle.getCustomer() == null || !vehicle.getCustomer().getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vehicle does not belong to this customer");
            }
        } else if (!authSupport.isManagement()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to view this vehicle");
        }

        return ResponseEntity.ok(vehicle);
    }

    @PostMapping("/me")
    public ResponseEntity<Vehicle> createMyVehicle(@Valid @RequestBody CreateVehicleRequest request) {
        AuthUser client = authSupport.requireClient();
        Vehicle createdVehicle = vehicleService.createVehicle(client.getId(), request);

        if (createdVehicle == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(createdVehicle);
    }

    @PostMapping("/customer/{customerId}")
    public ResponseEntity<Vehicle> createVehicle(
            @PathVariable Long customerId,
            @Valid @RequestBody CreateVehicleRequest request) {
        AuthUser user = authSupport.requireUser();
        if ("CLIENT".equals(user.getRole())) {
            if (!user.getId().equals(customerId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot add a vehicle for another customer");
            }
        } else {
            authSupport.requireCanManageWorkers();
        }

        Vehicle createdVehicle = vehicleService.createVehicle(customerId, request);

        if (createdVehicle == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(createdVehicle);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeMyVehicle(@PathVariable Long id) {
        AuthUser client = authSupport.requireClient();
        boolean removed = vehicleService.removeVehicleFromCustomer(client.getId(), id);

        if (!removed) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/customer/{customerId}")
    public ResponseEntity<Void> removeVehicle(
            @PathVariable Long id,
            @PathVariable Long customerId) {
        AuthUser user = authSupport.requireUser();
        if ("CLIENT".equals(user.getRole())) {
            if (!user.getId().equals(customerId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot remove another customer's vehicle");
            }
        } else {
            authSupport.requireCanManageWorkers();
        }

        boolean removed = vehicleService.removeVehicleFromCustomer(customerId, id);

        if (!removed) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }
}
