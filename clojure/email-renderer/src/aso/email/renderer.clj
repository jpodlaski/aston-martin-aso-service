(ns aso.email.renderer
  "Renders subject, plain-text, and HTML for booking lifecycle email events."
  (:require [aso.email.layout :as layout]
            [clojure.string :as str]))

(defn- event-label [event]
  (case event
    "created" "booking received"
    "status_changed" "status update"
    "technician_assigned" "drop-off instructions"
    "appointment_scheduled" "appointment confirmed"
    "work_plan_updated" "work plan updated"
    "booking_rejected" "booking declined"
    "booking_cancelled" "booking cancelled"
    "booking_completed" "service completed"
    (str event)))

(defn- format-status [s]
  (-> (str s)
      (str/replace "_" " ")))

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

(defn- cost-label-value [event status estimated-cost final-cost currency]
  (let [curr (or currency "EUR")]
    (cond
      (= event "created")
      ["Cost" "To be confirmed by the workshop"]

      (#{"appointment_scheduled" "technician_assigned" "work_plan_updated"} event)
      (if estimated-cost
        ["Estimated cost" (str estimated-cost " " curr)]
        ["Cost" "To be confirmed by the workshop"])

      (= event "status_changed")
      (cond
        (= status "COMPLETED")
        (if final-cost
          ["Final cost" (str final-cost " " curr)]
          ["Final cost" "N/A"])
        estimated-cost
        ["Estimated cost" (str estimated-cost " " curr)]
        :else
        ["Cost" "To be confirmed by the workshop"])

      (= event "booking_completed")
      (if final-cost
        ["Final cost" (str final-cost " " curr)]
        ["Final cost" "N/A"])

      estimated-cost
      ["Estimated cost" (str estimated-cost " " curr)]

      :else
      ["Cost" "To be confirmed by the workshop"])))

(defn- booking-details
  [{:keys [event customerDescription serviceTypes carModel status previousStatus
           estimatedCost finalCost currency estimatedDropOffTime availabilityNotes
           scheduledDateTime cancellationReason]}]
  (let [services (format-service-types serviceTypes)
        [cost-label cost-value] (cost-label-value event status estimatedCost finalCost currency)
        reported (or customerDescription "No description provided")
        show-services? (and services
                            (#{"appointment_scheduled" "technician_assigned"
                               "booking_completed" "work_plan_updated"
                               "status_changed"} event))
        show-reported? (or (= event "created")
                           (and (#{"booking_rejected" "booking_cancelled"} event)
                                (not show-services?))
                           (and (not show-services?)
                                (not (#{"appointment_scheduled" "technician_assigned"
                                        "booking_completed" "work_plan_updated"} event))))]
    (cond-> []
      carModel
      (conj ["Vehicle" carModel])
      show-services?
      (conj ["Services" services])
      (and show-reported? customerDescription)
      (conj ["You reported" reported])
      (and (= event "created") (not customerDescription))
      (conj ["You reported" reported])
      (and estimatedDropOffTime
           (#{"created" "booking_rejected" "booking_cancelled"} event))
      (conj ["Estimated drop-off" (format-customer-datetime estimatedDropOffTime)])
      (and availabilityNotes
           (not (str/blank? availabilityNotes))
           (#{"created" "booking_rejected" "booking_cancelled"} event))
      (conj ["Availability" availabilityNotes])
      (and scheduledDateTime
           (not (#{"work_plan_updated" "booking_completed"} event)))
      (conj ["Appointment" (format-customer-datetime scheduledDateTime)])
      (and previousStatus (= event "status_changed"))
      (conj ["Previous status" (format-status previousStatus)])
      (and status (#{"created" "status_changed"} event))
      (conj ["Status" (format-status status)])
      (and cancellationReason (not (str/blank? cancellationReason)))
      (conj ["Reason" cancellationReason])
      (and cost-label
           (not (#{"booking_rejected" "booking_cancelled"} event)))
      (conj [cost-label cost-value]))))

(defn render-booking-email
  [{:keys [event customerName carModel status bookingId cancelledBy] :as payload}]
  (let [name (or customerName "Customer")
        model (or carModel "your vehicle")
        booking-ref (when bookingId (str " #" bookingId))
        details (booking-details (assoc payload :carModel model :status (or status "unknown")))
        details-text (layout/details-text details)
        details-html (layout/details-table details)
        subject (case event
                  "created"
                  (str "Aston Martin ASO – booking received" booking-ref)
                  "technician_assigned"
                  (str "Aston Martin ASO – drop off your vehicle" booking-ref)
                  "status_changed"
                  (str "Aston Martin ASO – booking status: "
                       (format-status (or status "unknown")) booking-ref)
                  "appointment_scheduled"
                  (str "Aston Martin ASO – appointment confirmed" booking-ref)
                  "work_plan_updated"
                  (str "Aston Martin ASO – work plan updated" booking-ref)
                  "booking_rejected"
                  (str "Aston Martin ASO – booking declined" booking-ref)
                  "booking_cancelled"
                  (str "Aston Martin ASO – booking cancelled" booking-ref)
                  "booking_completed"
                  (str "Aston Martin ASO – service completed" booking-ref)
                  (str "Aston Martin ASO – " (event-label event) booking-ref))
        intro (case event
                "created"
                (str "We have received your service request for " model ". "
                     "This is not a confirmed appointment yet — a consultant will review "
                     "your request and confirm a visit time.")
                "technician_assigned"
                (str "A technician is ready for your " model ". "
                     "Please drop off your vehicle at the workshop at the scheduled time below.")
                "status_changed"
                (str "There is an update on your service request for " model ".")
                "appointment_scheduled"
                (str "Your appointment for " model " has been confirmed. "
                     "Please see the scheduled time below. "
                     "You will receive a separate message when a technician is ready "
                     "for your vehicle drop-off.")
                "work_plan_updated"
                (str "The planned work for your " model " has been updated. "
                     "Please review the details below.")
                "booking_rejected"
                (str "We are unable to accept your service request for " model
                     " at this time. You may submit a new request with different availability.")
                "booking_cancelled"
                (if (= cancelledBy "CUSTOMER")
                  (str "Your service request for " model " has been cancelled as requested.")
                  (str "Your service request for " model
                       " has been cancelled by the workshop."))
                "booking_completed"
                (str "Service on your " model " is complete. "
                     "Your invoice is attached to this email.")
                (str "There is an update regarding your service for " model "."))
        pickup-note (when (= event "booking_completed")
                      (str "You can pick up your vehicle during workshop opening hours, "
                           layout/workshop-hours "."))
        text-body (layout/wrap-text
                   name
                   (str intro
                        "\n\n" details-text
                        (when pickup-note (str "\n\n" pickup-note))))
        html-body (layout/wrap-html
                   name
                   (str (layout/p intro)
                        (or details-html "")
                        (when pickup-note (layout/p pickup-note))))]
    {:subject subject
     :textBody text-body
     :htmlBody html-body}))
