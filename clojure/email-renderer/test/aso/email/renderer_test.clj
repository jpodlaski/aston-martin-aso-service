(ns aso.email.renderer-test
  (:require [aso.email.customer-renderer :refer [render-customer-email]]
            [aso.email.renderer :refer [render-booking-email]]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(deftest render-created-email
  (testing "created event with availability"
    (let [result (render-booking-email
                  {:event "created"
                   :customerName "Jane Doe"
                   :customerDescription "Brakes squeaking and oil warning light is on"
                   :carModel "DBX"
                   :status "SCHEDULED"
                   :estimatedDropOffTime "2026-06-15T10:30:00"
                   :availabilityNotes "Tuesday mornings preferred"
                   :bookingId 42})]
      (is (str/includes? (:subject result) "booking received"))
      (is (not (str/includes? (:subject result) "confirmed")))
      (is (str/includes? (:textBody result) "Dear Jane Doe"))
      (is (str/includes? (:textBody result) "We have received your service request"))
      (is (str/includes? (:textBody result) "You reported: Brakes squeaking and oil warning light is on"))
      (is (str/includes? (:textBody result) "Estimated drop-off: 2026-06-15, Monday, 10:30"))
      (is (str/includes? (:textBody result) "Availability: Tuesday mornings preferred"))
      (is (str/includes? (:textBody result) "not a confirmed appointment"))
      (is (str/includes? (:textBody result) "consultant will review"))
      (is (str/includes? (:textBody result) "To be confirmed by the workshop"))
      (is (not (str/includes? (:textBody result) "Services:")))
      (is (str/includes? (:htmlBody result) "data:image/png;base64,"))
      (is (str/includes? (:htmlBody result) "#00393d"))
      (is (not (str/includes? (:htmlBody result) "Authorized Service")))
      (is (not (str/includes? (:htmlBody result) ">ASO Service<")))
      (is (str/includes? (:htmlBody result) "DBX")))))

(deftest render-technician-assigned-email
  (testing "technician_assigned event asks client to drop off the vehicle"
    (let [result (render-booking-email
                  {:event "technician_assigned"
                   :customerName "Jane Doe"
                   :customerDescription "Brakes squeaking"
                   :serviceTypes ["Brake inspection" "Oil change"]
                   :carModel "DB11"
                   :status "IN_PROGRESS"
                   :estimatedCost "350.00"
                   :currency "EUR"
                   :estimatedDropOffTime "2026-06-15T10:30:00"
                   :scheduledDateTime "2026-06-16T14:00:00"
                   :bookingId 42})]
      (is (str/includes? (:subject result) "drop off your vehicle"))
      (is (not (str/includes? (:subject result) "work started")))
      (is (not (str/includes? (:subject result) "IN PROGRESS")))
      (is (str/includes? (:textBody result) "Please drop off your vehicle"))
      (is (str/includes? (:textBody result) "Services: Brake inspection, Oil change"))
      (is (str/includes? (:textBody result) "Estimated cost: 350.00 EUR"))
      (is (str/includes? (:textBody result) "Appointment: 2026-06-16, Tuesday, 14:00"))
      (is (not (str/includes? (:textBody result) "Estimated drop-off")))
      (is (not (str/includes? (:textBody result) "Work on your"))))))

(deftest render-status-changed-email
  (testing "status_changed event with COMPLETED status"
    (let [result (render-booking-email
                  {:event "status_changed"
                   :customerName "Jane Doe"
                   :customerDescription "Brakes squeaking"
                   :serviceTypes ["Brake inspection"]
                   :carModel "DBX"
                   :status "COMPLETED"
                   :previousStatus "IN_PROGRESS"
                   :finalCost "375.50"
                   :currency "EUR"
                   :bookingId 42})]
      (is (str/includes? (:textBody result) "Previous status: IN PROGRESS"))
      (is (str/includes? (:textBody result) "Services: Brake inspection"))
      (is (str/includes? (:textBody result) "Final cost: 375.50 EUR"))
      (is (not (str/includes? (:textBody result) "IN_PROGRESS"))))))

(deftest render-appointment-scheduled-email
  (testing "appointment_scheduled event after consultant confirms"
    (let [result (render-booking-email
                  {:event "appointment_scheduled"
                   :customerName "Jane Doe"
                   :customerDescription "Brakes squeaking"
                   :serviceTypes ["Brake inspection"]
                   :carModel "DBX"
                   :status "READY_FOR_WORK"
                   :scheduledDateTime "2026-06-16T14:00:00"
                   :bookingId 42})]
      (is (str/includes? (:subject result) "appointment confirmed"))
      (is (str/includes? (:textBody result) "Appointment: 2026-06-16, Tuesday, 14:00"))
      (is (str/includes? (:textBody result) "has been confirmed"))
      (is (str/includes? (:textBody result) "technician is ready"))
      (is (str/includes? (:textBody result) "vehicle drop-off"))
      (is (not (str/includes? (:textBody result) "when work begins")))
      (is (not (str/includes? (:textBody result) "now in progress")))
      (is (str/includes? (:textBody result) "Services: Brake inspection"))
      (is (str/includes? (:htmlBody result) "2026-06-16, Tuesday, 14:00")))))

(deftest render-work-plan-updated-email
  (testing "work_plan_updated event"
    (let [result (render-booking-email
                  {:event "work_plan_updated"
                   :customerName "Jane Doe"
                   :customerDescription "Weird noises at back"
                   :serviceTypes ["Brake replacement"]
                   :carModel "DBX707 SUV"
                   :status "IN_PROGRESS"
                   :estimatedCost 5000
                   :currency "EUR"
                   :scheduledDateTime "2026-08-13T08:00:00"
                   :bookingId 5})]
      (is (str/includes? (:subject result) "work plan updated"))
      (is (str/includes? (:textBody result) "planned work for your DBX707 SUV has been updated"))
      (is (str/includes? (:textBody result) "Brake replacement"))
      (is (str/includes? (:textBody result) "Estimated cost: 5000 EUR"))
      (is (not (str/includes? (:textBody result) "Appointment:"))))))

(deftest render-booking-completed-email
  (testing "booking_completed event"
    (let [result (render-booking-email
                  {:event "booking_completed"
                   :customerName "Jane Doe"
                   :customerDescription "Brakes squeaking"
                   :serviceTypes ["Brake inspection"]
                   :carModel "DB11"
                   :status "COMPLETED"
                   :previousStatus "IN_PROGRESS"
                   :finalCost "375.50"
                   :currency "EUR"
                   :scheduledDateTime "2026-06-16T14:00:00"
                   :bookingId 42})]
      (is (str/includes? (:subject result) "service completed"))
      (is (str/includes? (:textBody result) "is complete"))
      (is (str/includes? (:textBody result) "Final cost: 375.50 EUR"))
      (is (str/includes? (:textBody result) "invoice is attached"))
      (is (str/includes? (:textBody result) "pick up your vehicle"))
      (is (str/includes? (:textBody result) "06:00–20:00")))))

(deftest render-booking-rejected-email
  (testing "booking_rejected event"
    (let [result (render-booking-email
                  {:event "booking_rejected"
                   :customerName "Jane Doe"
                   :customerDescription "Engine makes weird noises"
                   :carModel "DBX"
                   :status "CANCELLED"
                   :cancellationReason "No workshop capacity on requested date"
                   :estimatedDropOffTime "2026-06-15T10:30:00"
                   :bookingId 42})]
      (is (str/includes? (:subject result) "booking declined"))
      (is (str/includes? (:textBody result) "unable to accept your service request"))
      (is (str/includes? (:textBody result) "No workshop capacity on requested date")))))

(deftest render-booking-cancelled-email
  (testing "booking_cancelled by worker"
    (let [result (render-booking-email
                  {:event "booking_cancelled"
                   :customerName "Jane Doe"
                   :customerDescription "Engine makes weird noises"
                   :carModel "DBX"
                   :status "CANCELLED"
                   :cancelledBy "WORKER"
                   :cancellationReason "Parts unavailable"
                   :bookingId 42})]
      (is (str/includes? (:subject result) "booking cancelled"))
      (is (str/includes? (:textBody result) "cancelled by the workshop"))
      (is (str/includes? (:textBody result) "Parts unavailable"))))
  (testing "booking_cancelled by customer"
    (let [result (render-booking-email
                  {:event "booking_cancelled"
                   :customerName "Jane Doe"
                   :customerDescription "Engine makes weird noises"
                   :carModel "DBX"
                   :status "CANCELLED"
                   :cancelledBy "CUSTOMER"
                   :bookingId 42})]
      (is (str/includes? (:textBody result) "has been cancelled"))
      (is (not (str/includes? (:textBody result) "cancelled by the workshop"))))))

(deftest render-customer-emails
  (testing "customer_registered event"
    (let [result (render-customer-email
                  {:event "customer_registered"
                   :customerName "Jane Doe"
                   :actionUrl "http://localhost:5173/verify-email?token=abc"})]
      (is (str/includes? (:subject result) "welcome"))
      (is (str/includes? (:textBody result) "account has been registered"))
      (is (str/includes? (:textBody result) "http://localhost:5173/verify-email?token=abc"))
      (is (str/includes? (:htmlBody result) "Verify email"))
      (is (str/includes? (:htmlBody result) "data:image/png;base64,"))
      (is (not (str/includes? (:htmlBody result) "Authorized Service")))
      (is (not (str/includes? (:htmlBody result) ">ASO Service<")))
      (is (str/includes? (:htmlBody result) "Kind regards"))))
  (testing "email_verification event"
    (let [result (render-customer-email
                  {:event "email_verification"
                   :customerName "Jane Doe"
                   :actionUrl "http://localhost:5173/verify-email?token=abc"})]
      (is (str/includes? (:subject result) "verify"))
      (is (str/includes? (:textBody result) "http://localhost:5173/verify-email?token=abc"))
      (is (str/includes? (:htmlBody result) "Verify email"))))
  (testing "password_reset event"
    (let [result (render-customer-email
                  {:event "password_reset"
                   :customerName "Jane Doe"
                   :actionUrl "http://localhost:5173/reset-password?token=xyz"})]
      (is (str/includes? (:subject result) "reset"))
      (is (str/includes? (:textBody result) "http://localhost:5173/reset-password?token=xyz"))
      (is (str/includes? (:htmlBody result) "Reset password"))))
  (testing "password_changed event"
    (let [result (render-customer-email
                  {:event "password_changed"
                   :customerName "Jane Doe"})]
      (is (str/includes? (:subject result) "password changed"))
      (is (str/includes? (:textBody result) "password was changed successfully"))))
  (testing "account_deletion event"
    (let [result (render-customer-email
                  {:event "account_deletion"
                   :customerName "Jane Doe"
                   :actionUrl "http://localhost:5173/confirm-account-deletion?token=del"})]
      (is (str/includes? (:subject result) "confirm account deletion"))
      (is (str/includes? (:textBody result) "http://localhost:5173/confirm-account-deletion?token=del"))
      (is (str/includes? (:htmlBody result) "Confirm account deletion"))))
  (testing "account_deleted event"
    (let [result (render-customer-email
                  {:event "account_deleted"
                   :customerName "Jane Doe"})]
      (is (str/includes? (:subject result) "account deleted"))
      (is (str/includes? (:textBody result) "account has been deleted"))))
  (testing "vehicle_added event"
    (let [result (render-customer-email
                  {:event "vehicle_added"
                   :customerName "Jane Doe"
                   :carModel "DB12 Coupe"
                   :modelLine "DB12"
                   :productionYear 2024
                   :bodyStyle "Coupe"
                   :engine "4.0L Twin-Turbo V8"
                   :power "680 PS"
                   :transmission "8-speed Automatic"
                   :drivetrain "RWD"
                   :vin "SCFRMFCW7M12345"})]
      (is (str/includes? (:subject result) "vehicle added"))
      (is (str/includes? (:textBody result) "Model: DB12"))
      (is (not (str/includes? (:textBody result) "Body style")))
      (is (str/includes? (:textBody result) "Production year: 2024"))
      (is (str/includes? (:textBody result) "Engine: 4.0L Twin-Turbo V8"))
      (is (not (str/includes? (:textBody result) "present")))
      (is (str/includes? (:textBody result) "Transmission: 8-speed Automatic"))
      (is (str/includes? (:textBody result) "VIN: SCFRMFCW7M12345"))))
  (testing "vehicle_removed event"
    (let [result (render-customer-email
                  {:event "vehicle_removed"
                   :customerName "Jane Doe"
                   :carModel "DB12 Coupe"
                   :modelLine "DB12"
                   :productionYear 2024
                   :vin "SCFRMFCW7M12345"})]
      (is (str/includes? (:subject result) "vehicle removed"))
      (is (str/includes? (:textBody result) "removed from your account"))
      (is (str/includes? (:textBody result) "Model: DB12")))))
