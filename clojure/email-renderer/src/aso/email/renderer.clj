(ns aso.email.renderer
  "Renders subject, plain-text, and HTML for booking lifecycle email events."
  (:require [clojure.string :as str]))

(defn- event-label [event]
  (case event
    "created" "booking received"
    "status_changed" "status update"
    "technician_assigned" "technician assigned"
    "appointment_scheduled" "appointment confirmed"
    "booking_rejected" "booking declined"
    "booking_cancelled" "booking cancelled"
    "booking_completed" "service completed"
    (str event)))

(defn- format-status [s]
  (if (= s "IN_PROGRESS") "IN PROGRESS" s))

(defn- format-service-types [service-types]
  (when (seq service-types)
    (str/join ", " service-types)))

(defn- format-customer-datetime [value]
  (when (and value (not (str/blank? value)))
    (try
      (let [dt (java.time.LocalDateTime/parse value)
            fmt (java.time.format.DateTimeFormatter/ofPattern
                 "yyyy-MM-dd, EEEE, HH:mm"
                 java.util.Locale/ENGLISH)]
        (.format dt fmt))
      (catch Exception _
        value))))

(defn- availability-lines [estimated-drop-off-time availability-notes]
  (cond-> []
    estimated-drop-off-time
    (conj (str "Estimated drop-off: " (format-customer-datetime estimated-drop-off-time)))
    (and availability-notes (not (str/blank? availability-notes)))
    (conj (str "Availability: " availability-notes))))

(defn- availability-text-block [estimated-drop-off-time availability-notes]
  (let [lines (availability-lines estimated-drop-off-time availability-notes)]
    (when (seq lines)
      (str/join "\n" (map #(str % ".") lines)))))

(defn- availability-html-block [estimated-drop-off-time availability-notes]
  ;; Escape < only — user-provided text fields are otherwise inserted as plain text.
  (let [lines (availability-lines estimated-drop-off-time availability-notes)]
    (when (seq lines)
      (str/join "" (map #(str "<p>" (str/replace % "<" "&lt;") ".</p>") lines)))))

;; Which cost line to show depends on event type and current booking status.
(defn- cost-text-line [event status estimated-cost final-cost currency]
  (let [curr (or currency "EUR")]
    (cond
      (= event "created")
      "Cost: To be confirmed by workshop"

      (#{"appointment_scheduled" "technician_assigned"} event)
      (if estimated-cost
        (str "Estimated cost: " estimated-cost " " curr)
        "Cost: To be confirmed by workshop")

      (= event "status_changed")
      (cond
        (= status "COMPLETED")
        (if final-cost
          (str "Final cost: " final-cost " " curr)
          "Final cost: N/A")
        estimated-cost
        (str "Estimated cost: " estimated-cost " " curr)
        :else
        "Cost: To be confirmed by workshop")

      (= event "booking_completed")
      (if final-cost
        (str "Final cost: " final-cost " " curr)
        "Final cost: N/A")

      estimated-cost
      (str "Estimated cost: " estimated-cost " " curr)

      :else
      "Cost: To be confirmed by workshop")))

(defn- booking-detail-line [event customer-description service-types]
  (let [services (format-service-types service-types)
        reported (str "You reported: " (or customer-description "No description provided"))]
    (cond
      (= event "created")
      reported

      (#{"appointment_scheduled" "technician_assigned" "booking_completed"} event)
      (if services (str "Services: " services) reported)

      services
      (str "Services: " services)

      :else
      reported)))

(defn- reason-text-line [reason]
  (when (and reason (not (str/blank? reason)))
    (str "Reason: " reason)))

(defn render-booking-email
  [{:keys [event customerName customerDescription serviceTypes carModel status previousStatus
           bookingId estimatedCost finalCost currency estimatedDropOffTime availabilityNotes
           scheduledDateTime cancellationReason cancelledBy]}]
  (let [name (or customerName "Customer")
        model (or carModel "your vehicle")
        booking-ref (when bookingId (str " #" bookingId))
        raw-status (or status "unknown")
        status (format-status raw-status)
        previous-status (format-status previousStatus)
        detail-line (booking-detail-line event customerDescription serviceTypes)
        cost-line (cost-text-line event raw-status estimatedCost finalCost currency)
        availability-text (availability-text-block estimatedDropOffTime availabilityNotes)
        availability-html (availability-html-block estimatedDropOffTime availabilityNotes)
        appointment-line (when scheduledDateTime
                           (str "Appointment confirmed: "
                                (format-customer-datetime scheduledDateTime)))
        reason-line (reason-text-line cancellationReason)
        subject (case event
                  "created"
                  (str "Aston Martin ASO – booking received" booking-ref)
                  "technician_assigned"
                  (str "Aston Martin ASO – technician assigned" booking-ref)
                  "status_changed"
                  (str "Aston Martin ASO – booking status: " status booking-ref)
                  "appointment_scheduled"
                  (str "Aston Martin ASO – appointment confirmed" booking-ref)
                  "booking_rejected"
                  (str "Aston Martin ASO – booking declined" booking-ref)
                  "booking_cancelled"
                  (str "Aston Martin ASO – booking cancelled" booking-ref)
                  "booking_completed"
                  (str "Aston Martin ASO – service completed" booking-ref)
                  (str "Aston Martin ASO – " (event-label event) booking-ref))
        text-body (case event
                    "created"
                    (str "Hello " name ",\n\n"
                         "We've received your service request for " model ".\n"
                         detail-line ".\n"
                         (when availability-text (str availability-text "\n"))
                         "Current status: " status ".\n"
                         "This is not a confirmed appointment yet. Our workshop will review your request once a technician takes your booking.\n\n"
                         cost-line ".\n\n"
                         "Thank you for choosing Aston Martin ASO Service.")
                    "technician_assigned"
                    (str "Hello " name ",\n\n"
                         "A technician has been assigned to your service request for " model ".\n"
                         detail-line ".\n"
                         "They will contact you to confirm your appointment if needed.\n"
                         "Your booking is not in progress yet until the appointment is confirmed.\n\n"
                         cost-line ".\n\n"
                         "Thank you for choosing Aston Martin ASO Service.")
                    "status_changed"
                    (str "Hello " name ",\n\n"
                         "Your service appointment for " model " has been updated.\n"
                         detail-line ".\n"
                         "Previous status: " previous-status ".\n"
                         "New status: " status ".\n\n"
                         cost-line ".\n\n"
                         "Thank you for choosing Aston Martin ASO Service.")
                    "appointment_scheduled"
                    (str "Hello " name ",\n\n"
                         appointment-line ".\n"
                         detail-line ".\n"
                         "Vehicle: " model ".\n"
                         "Your service request is now in progress.\n\n"
                         "Thank you for choosing Aston Martin ASO Service.")
                    "booking_rejected"
                    (str "Hello " name ",\n\n"
                         "Unfortunately we cannot accept your service request for " model " at this time.\n"
                         detail-line ".\n"
                         (when availability-text (str availability-text "\n"))
                         (when reason-line (str reason-line ".\n"))
                         "\nYou may submit a new request with different availability.\n\n"
                         "Thank you for choosing Aston Martin ASO Service.")
                    "booking_cancelled"
                    (str "Hello " name ",\n\n"
                         (if (= cancelledBy "CUSTOMER")
                           (str "Your service request for " model " has been cancelled.\n")
                           (str "Your service request for " model " has been cancelled by the workshop.\n"))
                         detail-line ".\n"
                         (when reason-line (str reason-line ".\n"))
                         "\nThank you for choosing Aston Martin ASO Service.")
                    "booking_completed"
                    (str "Hello " name ",\n\n"
                         "Your service for " model " has been completed.\n"
                         detail-line ".\n"
                         (when appointment-line (str appointment-line ".\n"))
                         cost-line ".\n"
                         "Your invoice is attached to this email.\n\n"
                         "Thank you for choosing Aston Martin ASO Service.")
                    (str "Hello " name ",\n\n"
                         "Update regarding your service appointment for " model ".\n"
                         detail-line ".\n"
                         "Status: " status ".\n"
                         cost-line ".\n"))
        html-body (str "<html><body>"
                       "<p>Hello " (str/replace name "<" "&lt;") ",</p>"
                       (case event
                         "created"
                         (str "<p>We've received your service request for "
                              model ".</p>"
                              "<p>" (str/replace detail-line "<" "&lt;") ".</p>"
                              availability-html
                              "<p>Current status: <strong>" status "</strong>.</p>"
                              "<p>This is not a confirmed appointment yet. Our workshop will review your request once a technician takes your booking.</p>"
                              "<p>" cost-line ".</p>")
                         "technician_assigned"
                         (str "<p>A technician has been assigned to your service request for "
                              model ".</p>"
                              "<p>" (str/replace detail-line "<" "&lt;") ".</p>"
                              "<p>They will contact you to confirm your appointment if needed.</p>"
                              "<p>Your booking is not in progress yet until the appointment is confirmed.</p>"
                              "<p>" cost-line ".</p>")
                         "status_changed"
                         (str "<p>Your service appointment for "
                              model " has been updated.</p>"
                              "<p>" (str/replace detail-line "<" "&lt;") ".</p>"
                              "<p>Previous status: " previous-status "<br/>"
                              "New status: <strong>" status "</strong>.</p>"
                              "<p>" cost-line ".</p>")
                         "appointment_scheduled"
                         (str "<p><strong>" (str/replace appointment-line "<" "&lt;") ".</strong></p>"
                              "<p>" (str/replace detail-line "<" "&lt;") ".</p>"
                              "<p>Vehicle: " model ".</p>"
                              "<p>Your service request is now in progress.</p>")
                         "booking_rejected"
                         (str "<p>Unfortunately we cannot accept your service request for "
                              model " at this time.</p>"
                              "<p>" (str/replace detail-line "<" "&lt;") ".</p>"
                              availability-html
                              (when reason-line (str "<p>" (str/replace reason-line "<" "&lt;") ".</p>"))
                              "<p>You may submit a new request with different availability.</p>")
                         "booking_cancelled"
                         (str "<p>"
                              (if (= cancelledBy "CUSTOMER")
                                (str "Your service request for " model " has been cancelled.")
                                (str "Your service request for " model " has been cancelled by the workshop."))
                              "</p>"
                              "<p>" (str/replace detail-line "<" "&lt;") ".</p>"
                              (when reason-line (str "<p>" (str/replace reason-line "<" "&lt;") ".</p>")))
                         "booking_completed"
                         (str "<p>Your service for "
                              model " has been completed.</p>"
                              "<p>" (str/replace detail-line "<" "&lt;") ".</p>"
                              (when appointment-line
                                (str "<p>" (str/replace appointment-line "<" "&lt;") ".</p>"))
                              "<p>" cost-line ".</p>"
                              "<p>Your invoice is attached to this email.</p>")
                         (str "<p>Update regarding your service appointment for "
                              model ".</p>"
                              "<p>" (str/replace detail-line "<" "&lt;") ".</p>"
                              "<p>Status: <strong>" status "</strong>.</p>"
                              "<p>" cost-line ".</p>"))
                       "<p>Thank you for choosing Aston Martin ASO Service.</p>"
                       "</body></html>")]
    {:subject subject
     :textBody text-body
     :htmlBody html-body}))
