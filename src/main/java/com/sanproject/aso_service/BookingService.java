package com.sanproject.aso_service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookingService {

    private final ServiceBookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final BookingNotificationService notificationService;
    private final EstimatedCostService estimatedCostService;

    public BookingService(
            ServiceBookingRepository bookingRepository,
            VehicleRepository vehicleRepository,
            BookingNotificationService notificationService,
            EstimatedCostService estimatedCostService) {
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
        this.notificationService = notificationService;
        this.estimatedCostService = estimatedCostService;
    }

    public List<ServiceBooking> getAllBookings() {
        return bookingRepository.findAll();
    }

    public ServiceBooking getBookingById(Long id) {
        return bookingRepository.findById(id).orElse(null);
    }

    public ServiceBooking createBooking(CreateBookingRequest request) {
        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId()).orElse(null);

        if (vehicle == null) {
            return null;
        }

        ServiceBooking booking = new ServiceBooking();
        booking.setVehicle(vehicle);
        booking.setServiceType(request.getServiceType());
        booking.setStatus(BookingStatus.SCHEDULED);
        booking.setCarModel(vehicle.getModel());
        booking.setEstimatedCost(estimatedCostService.estimateCost(request.getServiceType()));

        Customer customer = vehicle.getCustomer();
        if (customer != null) {
            booking.setCustomerName(customer.getFirstName() + " " + customer.getLastName());
        }

        ServiceBooking saved = bookingRepository.save(booking);
        notificationService.notifyCreated(saved);
        return saved;
    }

    public boolean deleteBooking(Long id) {
        if (!bookingRepository.existsById(id)) {
            return false;
        }

        bookingRepository.deleteById(id);
        return true;
    }

    public ServiceBooking updateBooking(Long id, ServiceBooking updatedBooking) {
        ServiceBooking existingBooking = bookingRepository.findById(id).orElse(null);

        if (existingBooking == null) {
            return null;
        }

        existingBooking.setCustomerName(updatedBooking.getCustomerName());
        existingBooking.setCarModel(updatedBooking.getCarModel());
        existingBooking.setServiceType(updatedBooking.getServiceType());
        existingBooking.setStatus(updatedBooking.getStatus());
        existingBooking.setEstimatedCost(estimatedCostService.estimateCost(updatedBooking.getServiceType()));

        return bookingRepository.save(existingBooking);
    }

    public List<ServiceBooking> getBookingsByVehicleId(Long vehicleId) {
        return bookingRepository.findByVehicleId(vehicleId);
    }

    public List<ServiceBooking> getBookingsByCustomerId(Long customerId) {
        return bookingRepository.findByVehicleCustomerId(customerId);
    }

    public ServiceBooking updateStatus(Long id, UpdateBookingStatusRequest request) {
        ServiceBooking booking = bookingRepository.findById(id).orElse(null);

        if (booking == null) {
            return null;
        }

        BookingStatus previousStatus = booking.getStatus();
        booking.setStatus(request.getStatus());
        ServiceBooking saved = bookingRepository.save(booking);
        notificationService.notifyStatusChanged(saved, previousStatus);
        return saved;
    }

}
