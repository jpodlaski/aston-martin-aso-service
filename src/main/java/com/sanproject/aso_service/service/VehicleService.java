package com.sanproject.aso_service.service;

import com.sanproject.aso_service.catalog.ResolvedVehicleConfiguration;
import com.sanproject.aso_service.catalog.VehicleCatalogService;
import com.sanproject.aso_service.domain.BookingStatus;
import com.sanproject.aso_service.domain.Customer;
import com.sanproject.aso_service.domain.Vehicle;
import com.sanproject.aso_service.dto.CreateVehicleRequest;
import com.sanproject.aso_service.repository.CustomerRepository;
import com.sanproject.aso_service.repository.ServiceBookingRepository;
import com.sanproject.aso_service.repository.VehicleRepository;

import com.sanproject.aso_service.catalog.ResolvedVehicleConfiguration;
import com.sanproject.aso_service.catalog.VehicleCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Adds vehicles from the catalog; soft-removes with an open-booking guard.
 * Soft delete keeps the row so past bookings still resolve vehicle/VIN history.
 */
@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final CustomerRepository customerRepository;
    private final ServiceBookingRepository bookingRepository;
    private final CustomerNotificationService notificationService;
    private final VehicleCatalogService catalogService;

    public VehicleService(
            VehicleRepository vehicleRepository,
            CustomerRepository customerRepository,
            ServiceBookingRepository bookingRepository,
            CustomerNotificationService notificationService,
            VehicleCatalogService catalogService) {
        this.vehicleRepository = vehicleRepository;
        this.customerRepository = customerRepository;
        this.bookingRepository = bookingRepository;
        this.notificationService = notificationService;
        this.catalogService = catalogService;
    }

    public List<Vehicle> getAllVehicles() {
        return vehicleRepository.findAll();
    }

    public List<Vehicle> getVehiclesByCustomerId(Long customerId) {
        return vehicleRepository.findByCustomerIdAndRemovedFromAccountFalse(customerId);
    }

    /** Soft-removed vehicles still owned in history (sold / removed from account). */
    public List<Vehicle> getVehicleHistoryByCustomerId(Long customerId) {
        return vehicleRepository.findByCustomerIdAndRemovedFromAccountTrue(customerId);
    }

    public Vehicle getVehicleById(Long id) {
        return vehicleRepository.findById(id).orElse(null);
    }

    /** True when no active (non soft-removed) vehicle already uses this VIN. */
    public boolean isVinAvailable(String vin) {
        String normalized = normalizeVin(vin);
        if (normalized.isEmpty()) {
            return true;
        }
        return !vehicleRepository.existsByVinIgnoreCaseAndRemovedFromAccountFalse(normalized);
    }

    @Transactional
    public Vehicle createVehicle(Long customerId, CreateVehicleRequest request) {
        Customer customer = customerRepository.findById(customerId).orElse(null);

        if (customer == null) {
            return null;
        }

        String vin = normalizeVin(request.getVin());
        if (vin.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VIN is required");
        }
        requireVinAvailable(vin);

        // Denormalise catalog fields onto the vehicle row at creation time.
        ResolvedVehicleConfiguration configuration = catalogService.requireConfiguration(request.getConfigurationId());
        catalogService.validateProductionYear(configuration.getProductionEra(), request.getProductionYear());

        Vehicle vehicle = new Vehicle();
        vehicle.setVin(vin);
        vehicle.setConfigurationId(configuration.getConfigurationId());
        vehicle.setModelLine(configuration.getModelLine());
        vehicle.setModel(configuration.getModel());
        vehicle.setProductionEra(configuration.getProductionEra());
        vehicle.setProductionYear(request.getProductionYear());
        vehicle.setBodyStyle(configuration.getBodyStyle());
        vehicle.setEngine(configuration.getEngine());
        vehicle.setPower(configuration.getPower());
        vehicle.setTransmission(configuration.getTransmission());
        vehicle.setDrivetrain(configuration.getDrivetrain());
        vehicle.setCustomer(customer);

        Vehicle saved = vehicleRepository.save(vehicle);
        notificationService.notifyVehicleAdded(saved);
        return saved;
    }

    @Transactional
    public boolean removeVehicleFromCustomer(Long customerId, Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId).orElse(null);

        if (vehicle == null || vehicle.isRemovedFromAccount()) {
            return false;
        }

        if (vehicle.getCustomer() == null || !vehicle.getCustomer().getId().equals(customerId)) {
            return false;
        }

        // Soft-delete only when no pending or in-progress bookings reference this vehicle.
        if (bookingRepository.existsByVehicleIdAndStatusIn(
                vehicleId, List.of(BookingStatus.SCHEDULED, BookingStatus.READY_FOR_WORK, BookingStatus.IN_PROGRESS))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot remove a vehicle with open service bookings. Wait until they are completed.");
        }

        vehicle.setRemovedFromAccount(true);
        Vehicle saved = vehicleRepository.save(vehicle);
        notificationService.notifyVehicleRemoved(saved);
        return true;
    }

    private void requireVinAvailable(String vin) {
        if (!isVinAvailable(vin)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This VIN is already registered to a vehicle");
        }
    }

    public static String normalizeVin(String vin) {
        if (vin == null) {
            return "";
        }
        return vin.trim().replaceAll("\\s+", "").toUpperCase();
    }
}
