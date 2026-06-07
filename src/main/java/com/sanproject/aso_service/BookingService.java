package com.sanproject.aso_service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Booking state machine: SCHEDULED → IN_PROGRESS → COMPLETED, or CANCELLED via reject/cancel.
@Service
public class BookingService {

    private final ServiceBookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final WorkerRepository workerRepository;
    private final BookingNotificationService notificationService;

    public BookingService(
            ServiceBookingRepository bookingRepository,
            VehicleRepository vehicleRepository,
            WorkerRepository workerRepository,
            BookingNotificationService notificationService) {
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
        this.workerRepository = workerRepository;
        this.notificationService = notificationService;
    }

    public List<ServiceBooking> getAllBookings() {
        return bookingRepository.findAll();
    }

    public ServiceBooking getBookingById(Long id) {
        return bookingRepository.findById(id).orElse(null);
    }

    public List<ServiceBooking> getAvailableBookings() {
        return bookingRepository.findByAssignedWorkerIsNullAndStatus(BookingStatus.SCHEDULED);
    }

    public List<ServiceBooking> getBookingsByWorkerId(Long workerId) {
        return bookingRepository.findByAssignedWorkerId(workerId);
    }

    public ServiceBooking createBooking(CreateBookingRequest request) {
        validateAvailability(request);

        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId()).orElse(null);

        if (vehicle == null) {
            return null;
        }

        ServiceBooking booking = new ServiceBooking();
        booking.setVehicle(vehicle);
        booking.setCustomerDescription(request.getCustomerDescription());
        booking.setEstimatedDropOffTime(request.getEstimatedDropOffTime());
        booking.setAvailabilityNotes(request.getAvailabilityNotes());
        booking.setStatus(BookingStatus.SCHEDULED);
        booking.setCarModel(vehicle.getModel());

        Customer customer = vehicle.getCustomer();
        if (customer != null) {
            booking.setCustomerName(customer.getFirstName() + " " + customer.getLastName());
        }

        ServiceBooking saved = bookingRepository.save(booking);
        // Email runs async and reloads the booking by id.
        notificationService.notifyCreated(saved);
        return saved;
    }

    public ServiceBooking claimBooking(Long id, ClaimBookingRequest request) {
        ServiceBooking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null) {
            return null;
        }

        // Only unclaimed SCHEDULED bookings can be claimed.
        if (booking.getStatus() != BookingStatus.SCHEDULED || booking.getAssignedWorker() != null) {
            return null;
        }

        Worker worker = workerRepository.findById(request.getWorkerId()).orElse(null);
        if (worker == null) {
            return null;
        }

        booking.setAssignedWorker(worker);
        booking.setServiceTypes(new ArrayList<>(request.getServiceTypes()));
        booking.setEstimatedCost(request.getEstimatedCost());
        booking.setStatus(BookingStatus.IN_PROGRESS);

        ServiceBooking saved = bookingRepository.save(booking);
        notificationService.notifyTechnicianAssigned(saved);
        return saved;
    }

    public ServiceBooking scheduleBooking(Long id, ScheduleBookingRequest request) {
        ServiceBooking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null) {
            return null;
        }

        if (booking.getStatus() != BookingStatus.IN_PROGRESS
                || booking.getAssignedWorker() == null
                || !booking.getAssignedWorker().getId().equals(request.getWorkerId())) {
            return null;
        }

        validateFutureDateTime(request.getScheduledDateTime(), "scheduledDateTime");

        booking.setScheduledDateTime(request.getScheduledDateTime());
        ServiceBooking saved = bookingRepository.save(booking);
        notificationService.notifyAppointmentScheduled(saved);
        return saved;
    }

    public ServiceBooking rejectBooking(Long id, RejectBookingRequest request) {
        ServiceBooking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null) {
            return null;
        }

        if (booking.getStatus() != BookingStatus.SCHEDULED || booking.getAssignedWorker() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only unclaimed scheduled bookings can be rejected");
        }

        if (workerRepository.findById(request.getWorkerId()).isEmpty()) {
            return null;
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(request.getReason().trim());
        booking.setCancelledBy(CancelledBy.WORKSHOP); // reject is a workshop-initiated cancellation

        ServiceBooking saved = bookingRepository.save(booking);
        notificationService.notifyRejected(saved);
        return saved;
    }

    public ServiceBooking cancelBooking(Long id, CancelBookingRequest request) {
        ServiceBooking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null) {
            return null;
        }

        if (booking.getStatus() != BookingStatus.SCHEDULED
                && booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only scheduled or in-progress bookings can be cancelled");
        }

        // Exactly one of customerId or workerId must be provided (XOR).
        boolean cancelledByCustomer = request.getCustomerId() != null;
        boolean cancelledByWorker = request.getWorkerId() != null;

        if (cancelledByCustomer == cancelledByWorker) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provide either customerId or workerId");
        }

        if (cancelledByCustomer) {
            if (!ownsBooking(request.getCustomerId(), booking)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Booking does not belong to this customer");
            }
            booking.setCancelledBy(CancelledBy.CUSTOMER);
            booking.setCancellationReason(null); // customer cancel needs no reason
        } else {
            if (booking.getAssignedWorker() == null
                    || !booking.getAssignedWorker().getId().equals(request.getWorkerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Booking is not assigned to this worker");
            }
            if (request.getReason() == null || request.getReason().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason is required when cancelling as worker");
            }
            booking.setCancelledBy(CancelledBy.WORKER);
            booking.setCancellationReason(request.getReason().trim());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        ServiceBooking saved = bookingRepository.save(booking);
        notificationService.notifyCancelled(saved);
        return saved;
    }

    public ServiceBooking completeBooking(Long id, CompleteBookingRequest request) {
        ServiceBooking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null) {
            return null;
        }

        if (booking.getStatus() != BookingStatus.IN_PROGRESS
                || booking.getAssignedWorker() == null
                || !booking.getAssignedWorker().getId().equals(request.getWorkerId())) {
            return null;
        }

        BookingStatus previousStatus = booking.getStatus();
        booking.setFinalCost(request.getFinalCost());
        booking.setStatus(BookingStatus.COMPLETED);

        ServiceBooking saved = bookingRepository.save(booking);
        notificationService.notifyCompleted(saved, previousStatus);
        return saved;
    }

    // Eager-fetched query avoids lazy-load errors when mapping nested vehicle/worker data.
    public List<AdminBookingResponse> getAdminBookings() {
        return bookingRepository.findAllWithDetails().stream()
                .map(this::toAdminBookingResponse)
                .toList();
    }

    private AdminBookingResponse toAdminBookingResponse(ServiceBooking booking) {
        AdminBookingResponse response = new AdminBookingResponse();
        response.setId(booking.getId());
        response.setCustomerName(booking.getCustomerName());
        response.setCustomerDescription(booking.getCustomerDescription());
        response.setCarModel(booking.getCarModel());
        response.setStatus(booking.getStatus());
        response.setServiceTypes(booking.getServiceTypes());
        response.setEstimatedCost(booking.getEstimatedCost());
        response.setFinalCost(booking.getFinalCost());
        response.setEstimatedDropOffTime(booking.getEstimatedDropOffTime());
        response.setAvailabilityNotes(booking.getAvailabilityNotes());
        response.setScheduledDateTime(booking.getScheduledDateTime());
        response.setCancellationReason(booking.getCancellationReason());
        response.setCancelledBy(booking.getCancelledBy());

        Vehicle vehicle = booking.getVehicle();
        if (vehicle != null) {
            response.setModelLine(vehicle.getModelLine());
            response.setVin(vehicle.getVin());
            if (vehicle.getCustomer() != null) {
                response.setCustomerEmail(vehicle.getCustomer().getEmail());
            }
        }

        Worker worker = booking.getAssignedWorker();
        if (worker != null) {
            response.setAssignedWorkerId(worker.getId());
            response.setAssignedWorkerName(worker.getFirstName() + " " + worker.getLastName());
            response.setAssignedWorkerRole(worker.getRole());
        }

        return response;
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
        if (updatedBooking.getCustomerDescription() != null) {
            existingBooking.setCustomerDescription(updatedBooking.getCustomerDescription());
        }
        existingBooking.setStatus(updatedBooking.getStatus());

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

    private boolean ownsBooking(Long customerId, ServiceBooking booking) {
        Vehicle vehicle = booking.getVehicle();
        return vehicle != null
                && vehicle.getCustomer() != null
                && vehicle.getCustomer().getId().equals(customerId);
    }

    // Customer must supply at least a preferred drop-off time or free-text availability notes.
    private void validateAvailability(CreateBookingRequest request) {
        boolean hasTime = request.getEstimatedDropOffTime() != null;
        boolean hasNotes = request.getAvailabilityNotes() != null
                && !request.getAvailabilityNotes().isBlank();

        if (!hasTime && !hasNotes) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Either estimatedDropOffTime or availabilityNotes is required");
        }

        if (hasTime) {
            validateFutureDateTime(request.getEstimatedDropOffTime(), "estimatedDropOffTime");
        }
    }

    private void validateFutureDateTime(LocalDateTime dateTime, String fieldName) {
        if (!dateTime.isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " must be in the future");
        }
    }
}
