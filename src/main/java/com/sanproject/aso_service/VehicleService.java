package com.sanproject.aso_service;

import com.sanproject.aso_service.catalog.ResolvedVehicleConfiguration;
import com.sanproject.aso_service.catalog.VehicleCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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

    public Vehicle getVehicleById(Long id) {
        return vehicleRepository.findById(id).orElse(null);
    }

    public Vehicle createVehicle(Long customerId, CreateVehicleRequest request) {
        Customer customer = customerRepository.findById(customerId).orElse(null);

        if (customer == null) {
            return null;
        }

        // Denormalise catalog fields onto the vehicle row at creation time.
        ResolvedVehicleConfiguration configuration = catalogService.requireConfiguration(request.getConfigurationId());
        catalogService.validateProductionYear(configuration.getProductionEra(), request.getProductionYear());

        Vehicle vehicle = new Vehicle();
        vehicle.setVin(request.getVin().trim());
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
                vehicleId, List.of(BookingStatus.SCHEDULED, BookingStatus.IN_PROGRESS))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot remove a vehicle with open service bookings. Wait until they are completed.");
        }

        vehicle.setRemovedFromAccount(true);
        Vehicle saved = vehicleRepository.save(vehicle);
        notificationService.notifyVehicleRemoved(saved);
        return true;
    }
}
