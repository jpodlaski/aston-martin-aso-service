package com.sanproject.aso_service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final CustomerRepository customerRepository;

    public VehicleService(VehicleRepository vehicleRepository, CustomerRepository customerRepository) {
        this.vehicleRepository = vehicleRepository;
        this.customerRepository = customerRepository;
    }

    public List<Vehicle> getAllVehicles() {
        return vehicleRepository.findAll();
    }

    public Vehicle getVehicleById(Long id) {
        return vehicleRepository.findById(id).orElse(null);
    }

    public Vehicle createVehicle(Long customerId, Vehicle vehicle) {
        Customer customer = customerRepository.findById(customerId).orElse(null);

        if (customer == null) {
            return null;
        }

        vehicle.setCustomer(customer);
        return vehicleRepository.save(vehicle);
    }
}
