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
    private final BookingNotificationService notificationService;

    public BookingService(
            ServiceBookingRepository bookingRepository,
            VehicleRepository vehicleRepository,
            BookingNotificationService notificationService) {
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
        this.notificationService = notificationService;
    }

    public List<ServiceBooking> getAllBookings() {
        return bookingRepository.findAllWithDetails();
    }

    public ServiceBooking getBookingById(Long id) {
        return bookingRepository.findByIdWithDetails(id).orElse(null);
    }

    public List<ServiceBooking> getAvailableBookings() {
        return bookingRepository.findAvailableWithDetails(BookingStatus.SCHEDULED);
    }

    public List<ServiceBooking> getBookingsByWorkerId(Long workerId) {
        return bookingRepository.findByWorkerIdWithDetails(workerId);
    }

    public ServiceBooking createBooking(CreateBookingRequest request, Long customerId) {
        validateAvailability(request);

        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId()).orElse(null);

        if (vehicle == null || vehicle.isRemovedFromAccount()) {
            return null;
        }

        if (vehicle.getCustomer() == null || !vehicle.getCustomer().getId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vehicle does not belong to this customer");
        }

        ServiceBooking booking = new ServiceBooking();
        booking.setVehicle(vehicle);
        booking.setCustomerDescription(request.getCustomerDescription());
        booking.setEstimatedDropOffTime(request.getEstimatedDropOffTime());
        booking.setAvailabilityNotes(request.getAvailabilityNotes());
        booking.setStatus(BookingStatus.SCHEDULED);
        booking.setCarModel(vehicle.getModel());

        Customer customer = vehicle.getCustomer();
        booking.setCustomerName(customer.getFirstName() + " " + customer.getLastName());

        ServiceBooking saved = bookingRepository.save(booking);
        notificationService.notifyCreated(saved);
        return reloadForResponse(saved.getId());
    }

    public ServiceBooking claimBooking(Long id, ClaimBookingRequest request, Worker worker) {
        ServiceBooking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null) {
            return null;
        }

        if (booking.getStatus() != BookingStatus.SCHEDULED || booking.getAssignedWorker() != null) {
            return null;
        }

        booking.setAssignedWorker(worker);
        booking.setServiceTypes(new ArrayList<>(request.getServiceTypes()));
        booking.setEstimatedCost(request.getEstimatedCost());
        booking.setStatus(BookingStatus.IN_PROGRESS);

        ServiceBooking saved = bookingRepository.save(booking);
        notificationService.notifyTechnicianAssigned(saved);
        return reloadForResponse(saved.getId());
    }

    public ServiceBooking scheduleBooking(Long id, ScheduleBookingRequest request, Worker worker) {
        ServiceBooking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null) {
            return null;
        }

        if (booking.getStatus() != BookingStatus.IN_PROGRESS
                || booking.getAssignedWorker() == null
                || !booking.getAssignedWorker().getId().equals(worker.getId())) {
            return null;
        }

        validateFutureDateTime(request.getScheduledDateTime(), "scheduledDateTime");

        booking.setScheduledDateTime(request.getScheduledDateTime());
        ServiceBooking saved = bookingRepository.save(booking);
        notificationService.notifyAppointmentScheduled(saved);
        return reloadForResponse(saved.getId());
    }

    public ServiceBooking rejectBooking(Long id, RejectBookingRequest request, Worker worker) {
        ServiceBooking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null) {
            return null;
        }

        if (booking.getStatus() != BookingStatus.SCHEDULED || booking.getAssignedWorker() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only unclaimed scheduled bookings can be rejected");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(request.getReason().trim());
        booking.setCancelledBy(CancelledBy.WORKSHOP);

        ServiceBooking saved = bookingRepository.save(booking);
        notificationService.notifyRejected(saved);
        return reloadForResponse(saved.getId());
    }

    public ServiceBooking cancelBooking(Long id, CancelBookingRequest request, AuthUser actor) {
        ServiceBooking booking = bookingRepository.findByIdWithDetails(id).orElse(null);
        if (booking == null) {
            return null;
        }

        if (booking.getStatus() != BookingStatus.SCHEDULED
                && booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only scheduled or in-progress bookings can be cancelled");
        }

        if ("CLIENT".equals(actor.getRole())) {
            if (!ownsBooking(actor.getId(), booking)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Booking does not belong to this customer");
            }
            booking.setCancelledBy(CancelledBy.CUSTOMER);
            booking.setCancellationReason(null);
        } else {
            EmployeeRole role;
            try {
                role = EmployeeRole.valueOf(actor.getRole());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to cancel this booking");
            }
            if (!role.isWorkshopStaff()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to cancel this booking");
            }
            if (booking.getAssignedWorker() == null
                    || !booking.getAssignedWorker().getId().equals(actor.getId())) {
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
        return reloadForResponse(saved.getId());
    }

    public ServiceBooking completeBooking(Long id, CompleteBookingRequest request, Worker worker) {
        ServiceBooking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null) {
            return null;
        }

        if (booking.getStatus() != BookingStatus.IN_PROGRESS
                || booking.getAssignedWorker() == null
                || !booking.getAssignedWorker().getId().equals(worker.getId())) {
            return null;
        }

        BookingStatus previousStatus = booking.getStatus();
        booking.setFinalCost(request.getFinalCost());
        booking.setStatus(BookingStatus.COMPLETED);

        ServiceBooking saved = bookingRepository.save(booking);
        notificationService.notifyCompleted(saved, previousStatus);
        return reloadForResponse(saved.getId());
    }

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

    public List<ServiceBooking> getBookingsByVehicleId(Long vehicleId) {
        return bookingRepository.findByVehicleIdWithDetails(vehicleId);
    }

    public List<ServiceBooking> getBookingsByCustomerId(Long customerId) {
        return bookingRepository.findByCustomerIdWithDetails(customerId);
    }

    // Reload with JOIN FETCH so JSON serialization works with open-in-view=false.
    private ServiceBooking reloadForResponse(Long id) {
        return bookingRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    // CLIENT: own bookings. Workshop: available queue or assigned. Management: all.
    public void assertCanView(AuthUser user, ServiceBooking booking) {
        if ("CLIENT".equals(user.getRole())) {
            if (!ownsBooking(user.getId(), booking)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Booking does not belong to this customer");
            }
            return;
        }

        EmployeeRole role;
        try {
            role = EmployeeRole.valueOf(user.getRole());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to view this booking");
        }

        if (role.canManageWorkers()) {
            return;
        }

        if (role.isWorkshopStaff()) {
            if (booking.getAssignedWorker() != null
                    && booking.getAssignedWorker().getId().equals(user.getId())) {
                return;
            }
            if (booking.getAssignedWorker() == null
                    && booking.getStatus() == BookingStatus.SCHEDULED) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to view this booking");
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to view this booking");
    }

    private boolean ownsBooking(Long customerId, ServiceBooking booking) {
        Vehicle vehicle = booking.getVehicle();
        return vehicle != null
                && vehicle.getCustomer() != null
                && vehicle.getCustomer().getId().equals(customerId);
    }

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
