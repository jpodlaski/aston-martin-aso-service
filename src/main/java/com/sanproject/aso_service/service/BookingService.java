package com.sanproject.aso_service.service;

import com.sanproject.aso_service.domain.BookingStatus;
import com.sanproject.aso_service.domain.CancelledBy;
import com.sanproject.aso_service.domain.Customer;
import com.sanproject.aso_service.domain.EmployeeRole;
import com.sanproject.aso_service.domain.ServiceBooking;
import com.sanproject.aso_service.domain.Vehicle;
import com.sanproject.aso_service.domain.Worker;
import com.sanproject.aso_service.dto.AdminBookingResponse;
import com.sanproject.aso_service.dto.CancelBookingRequest;
import com.sanproject.aso_service.dto.ClaimBookingRequest;
import com.sanproject.aso_service.dto.CompleteBookingRequest;
import com.sanproject.aso_service.dto.CreateBookingRequest;
import com.sanproject.aso_service.dto.RejectBookingRequest;
import com.sanproject.aso_service.dto.ScheduleBookingRequest;
import com.sanproject.aso_service.dto.UpdateWorkPlanRequest;
import com.sanproject.aso_service.dto.WorkshopCapacityResponse;
import com.sanproject.aso_service.repository.ServiceBookingRepository;
import com.sanproject.aso_service.repository.VehicleRepository;
import com.sanproject.aso_service.repository.WorkerRepository;
import com.sanproject.aso_service.security.AuthUser;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Core domain logic: the booking state machine.
 *
 *   SCHEDULED ──consultant accept──► READY_FOR_WORK ──technician claim──► IN_PROGRESS ──complete──► COMPLETED
 *       │                                    │                                 │
 *       │ reject                             │ cancel                          │ cancel
 *       └────────────────────────────────────┴─────────────────────────────────┴──► CANCELLED
 *
 * SCHEDULED = client request awaiting consultant.
 * READY_FOR_WORK = appointment confirmed; waiting for a technician to claim.
 * Every transition that succeeds enqueues a customer email via BookingNotificationService
 * (transactional outbox — same DB commit as the booking change).
 */
@Service
public class BookingService {

    private final ServiceBookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final WorkerRepository workerRepository;
    private final BookingNotificationService notificationService;
    private final InvoicePdfService invoicePdfService;

    public BookingService(
            ServiceBookingRepository bookingRepository,
            VehicleRepository vehicleRepository,
            WorkerRepository workerRepository,
            BookingNotificationService notificationService,
            InvoicePdfService invoicePdfService) {
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
        this.workerRepository = workerRepository;
        this.notificationService = notificationService;
        this.invoicePdfService = invoicePdfService;
    }

    public List<ServiceBooking> getAllBookings() {
        return bookingRepository.findAllWithDetails();
    }

    public ServiceBooking getBookingById(Long id) {
        return bookingRepository.findByIdWithDetails(id).orElse(null);
    }

    /**
     * Role-filtered intake / workshop queue:
     * consultant → SCHEDULED; technician → READY_FOR_WORK.
     */
    public List<ServiceBooking> getAvailableBookings(Worker worker) {
        EmployeeRole role = worker.getRole();
        if (role != null && role.isConsultant()) {
            return bookingRepository.findAvailableWithDetails(BookingStatus.SCHEDULED);
        }
        if (role != null && role.isTechnician()) {
            return bookingRepository.findAvailableWithDetails(BookingStatus.READY_FOR_WORK);
        }
        return List.of();
    }

    /** Consultant view of workshop jobs: awaiting claim or currently in progress. */
    public List<ServiceBooking> getAwaitingWorkshopBookings(Worker worker) {
        requireConsultant(worker);
        return bookingRepository.findByStatusesWithDetails(
                List.of(BookingStatus.READY_FOR_WORK, BookingStatus.IN_PROGRESS));
    }

    /** Consultant archive: completed services and cancelled requests. */
    public List<ServiceBooking> getConsultantArchiveBookings(Worker worker) {
        requireConsultant(worker);
        return bookingRepository.findByStatusesWithDetails(
                List.of(BookingStatus.COMPLETED, BookingStatus.CANCELLED));
    }

    /**
     * How many technician slots are free at a proposed appointment time.
     * An open job (READY_FOR_WORK / IN_PROGRESS) occupies one mechanic from its
     * appointment time until it is completed or cancelled.
     */
    public WorkshopCapacityResponse getWorkshopCapacity(LocalDateTime at, Worker worker) {
        requireConsultant(worker);
        return workshopCapacityAt(at);
    }

    public List<ServiceBooking> getBookingsByWorkerId(Long workerId) {
        return bookingRepository.findByWorkerIdWithDetails(workerId);
    }

    /** Client creates a request; vehicle ownership is checked so you cannot book someone else's car. */
    @Transactional
    public ServiceBooking createBooking(CreateBookingRequest request, Long customerId) {
        validateAvailability(request);
        if (request.descriptionExceedsWordLimit()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Description must be at most 500 words");
        }

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

    /**
     * Consultant accepts a client request: sets appointment → READY_FOR_WORK (unassigned).
     * Technicians then see it in their available queue.
     */
    @Transactional
    public ServiceBooking acceptBooking(Long id, ScheduleBookingRequest request, Worker worker) {
        requireConsultant(worker);

        ServiceBooking booking = bookingRepository.findByIdForUpdate(id).orElse(null);
        if (booking == null) {
            return null;
        }

        if (booking.getStatus() != BookingStatus.SCHEDULED || booking.getAssignedWorker() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only unclaimed scheduled requests can be accepted");
        }

        validateFutureDateTime(request.getScheduledDateTime(), "scheduledDateTime");

        WorkshopCapacityResponse capacity = workshopCapacityAt(request.getScheduledDateTime());
        if (!capacity.isAvailable()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No mechanic free at that time. Call the client to agree another appointment.");
        }

        booking.setScheduledDateTime(request.getScheduledDateTime());
        booking.setStatus(BookingStatus.READY_FOR_WORK);

        ServiceBooking saved = bookingRepository.save(booking);
        notificationService.notifyAppointmentScheduled(saved);
        return reloadForResponse(saved.getId());
    }

    /**
     * Technician takes an accepted READY_FOR_WORK booking → IN_PROGRESS.
     * Worker identity comes from the JWT (passed in by the controller), not from the request body.
     * Pessimistic row lock (findByIdForUpdate) serializes concurrent claims so only one wins.
     */
    @Transactional
    public ServiceBooking claimBooking(Long id, ClaimBookingRequest request, Worker worker) {
        requireTechnician(worker);

        ServiceBooking booking = bookingRepository.findByIdForUpdate(id).orElse(null);
        if (booking == null) {
            return null;
        }

        if (booking.getStatus() != BookingStatus.READY_FOR_WORK || booking.getAssignedWorker() != null) {
            return null;
        }

        if (bookingRepository.existsByAssignedWorkerIdAndStatus(worker.getId(), BookingStatus.IN_PROGRESS)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Finish your current assignment before claiming another job");
        }

        booking.setAssignedWorker(worker);
        booking.setServiceTypes(new ArrayList<>(request.getServiceTypes()));
        booking.setEstimatedCost(request.getEstimatedCost());
        booking.setStatus(BookingStatus.IN_PROGRESS);

        ServiceBooking saved = bookingRepository.save(booking);
        notificationService.notifyTechnicianAssigned(saved);
        return reloadForResponse(saved.getId());
    }

    /**
     * Assigned technician revises planned services / estimate after inspecting the car
     * (e.g. "car check" → "brake replace") while the booking is still IN_PROGRESS.
     */
    @Transactional
    public ServiceBooking updateWorkPlan(Long id, UpdateWorkPlanRequest request, Worker worker) {
        requireTechnician(worker);

        ServiceBooking booking = bookingRepository.findByIdWithDetails(id).orElse(null);
        if (booking == null) {
            return null;
        }

        if (booking.getStatus() != BookingStatus.IN_PROGRESS
                || booking.getAssignedWorker() == null
                || !booking.getAssignedWorker().getId().equals(worker.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only the assigned technician can update work planned on an in-progress booking");
        }

        // Mutate the managed collection so ElementCollection rows are replaced correctly.
        booking.getServiceTypes().clear();
        booking.getServiceTypes().addAll(request.getServiceTypes());
        if (request.getEstimatedCost() != null) {
            booking.setEstimatedCost(request.getEstimatedCost());
        }

        ServiceBooking saved = bookingRepository.save(booking);
        notificationService.notifyWorkPlanUpdated(saved);
        return reloadForResponse(saved.getId());
    }

    /**
     * Decline an unclaimed client request (still awaiting consultant).
     * Same row lock as accept so a concurrent accept/reject cannot both succeed.
     */
    @Transactional
    public ServiceBooking rejectBooking(Long id, RejectBookingRequest request, Worker worker) {
        requireConsultant(worker);

        ServiceBooking booking = bookingRepository.findByIdForUpdate(id).orElse(null);
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

    /**
     * Cancel rules differ by role:
     * - CLIENT may cancel their own open booking (SCHEDULED, READY_FOR_WORK, or IN_PROGRESS)
     * - Consultant may cancel READY_FOR_WORK (awaiting technician) with a reason
     * - Assigned technician may cancel IN_PROGRESS with a reason
     */
    @Transactional
    public ServiceBooking cancelBooking(Long id, CancelBookingRequest request, AuthUser actor) {
        ServiceBooking booking = bookingRepository.findByIdWithDetails(id).orElse(null);
        if (booking == null) {
            return null;
        }

        if (booking.getStatus() != BookingStatus.SCHEDULED
                && booking.getStatus() != BookingStatus.READY_FOR_WORK
                && booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only open bookings can be cancelled");
        }

        if ("CLIENT".equals(actor.getRole())) {
            if (!ownsBooking(actor.getId(), booking)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Booking does not belong to this customer");
            }
            if (request.getReason() == null || request.getReason().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason is required when cancelling a booking");
            }
            booking.setCancelledBy(CancelledBy.CUSTOMER);
            booking.setCancellationReason(request.getReason().trim());
        } else {
            EmployeeRole role;
            try {
                role = EmployeeRole.valueOf(actor.getRole());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to cancel this booking");
            }

            if (request.getReason() == null || request.getReason().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason is required when cancelling as staff");
            }

            if (role.isConsultant()) {
                if (booking.getStatus() != BookingStatus.READY_FOR_WORK
                        || booking.getAssignedWorker() != null) {
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Consultants can only cancel bookings awaiting workshop assignment");
                }
                booking.setCancelledBy(CancelledBy.WORKSHOP);
                booking.setCancellationReason(request.getReason().trim());
            } else if (role.isTechnician()) {
                if (booking.getAssignedWorker() == null
                        || !booking.getAssignedWorker().getId().equals(actor.getId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Booking is not assigned to this worker");
                }
                booking.setCancelledBy(CancelledBy.WORKER);
                booking.setCancellationReason(request.getReason().trim());
            } else {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to cancel this booking");
            }
        }

        booking.setStatus(BookingStatus.CANCELLED);
        ServiceBooking saved = bookingRepository.save(booking);
        notificationService.notifyCancelled(saved);
        return reloadForResponse(saved.getId());
    }

    /** Finish work: set final cost → COMPLETED; completion email attaches a generated PDF invoice. */
    @Transactional
    public ServiceBooking completeBooking(Long id, CompleteBookingRequest request, Worker worker) {
        requireTechnician(worker);

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

    /**
     * Same PDF attached to the completion email — regenerated on demand for the owning client.
     */
    public byte[] getInvoicePdfForCustomer(Long bookingId, Long customerId) {
        ServiceBooking booking = bookingRepository.findByIdWithDetails(bookingId).orElse(null);
        if (booking == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        if (!ownsBooking(customerId, booking)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Booking does not belong to this customer");
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invoice is available only after the booking is completed");
        }

        Customer customer = booking.getVehicle().getCustomer();
        return invoicePdfService.createInvoicePdf(booking, customer);
    }

    /**
     * open-in-view=false means Hibernate is closed after the service method returns.
     * Reloading with JOIN FETCH ensures vehicle/worker/serviceTypes are loaded before JSON serialization.
     */
    private ServiceBooking reloadForResponse(Long id) {
        return bookingRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    /**
     * Authorization matrix for GET /bookings/{id}:
     * CLIENT → own bookings only; workshop → role-appropriate queue or assigned; management → all.
     */
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
            if (booking.getAssignedWorker() == null) {
                if (role.isConsultant()
                        && (booking.getStatus() == BookingStatus.SCHEDULED
                        || booking.getStatus() == BookingStatus.READY_FOR_WORK
                        || booking.getStatus() == BookingStatus.CANCELLED)) {
                    return;
                }
                if (role.isTechnician()
                        && booking.getStatus() == BookingStatus.READY_FOR_WORK) {
                    return;
                }
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

    private void requireConsultant(Worker worker) {
        if (worker.getRole() == null || !worker.getRole().isConsultant()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Consultant access required");
        }
    }

    private void requireTechnician(Worker worker) {
        if (worker.getRole() == null || !worker.getRole().isTechnician()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Technician access required");
        }
    }

    private WorkshopCapacityResponse workshopCapacityAt(LocalDateTime at) {
        long technicianCount = workerRepository.countByRoleIn(
                List.of(EmployeeRole.MECHANIC, EmployeeRole.APPRENTICE_MECHANIC));
        // Open jobs hold a mechanic from appointment time until complete/cancel.
        long bookedCount = bookingRepository.countBusyAt(
                at,
                List.of(BookingStatus.READY_FOR_WORK, BookingStatus.IN_PROGRESS));
        long remainingSlots = Math.max(0, technicianCount - bookedCount);
        boolean available = remainingSlots > 0;
        return new WorkshopCapacityResponse(at, technicianCount, bookedCount, remainingSlots, available);
    }

    private void validateAvailability(CreateBookingRequest request) {
        if (request.getEstimatedDropOffTime() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "estimatedDropOffTime is required");
        }

        validateFutureDateTime(request.getEstimatedDropOffTime(), "estimatedDropOffTime");
    }

    private void validateFutureDateTime(LocalDateTime dateTime, String fieldName) {
        if (!dateTime.isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " must be in the future");
        }
    }
}
