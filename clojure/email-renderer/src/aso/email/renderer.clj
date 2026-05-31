(ns aso.email.renderer
  (:require [clojure.string :as str]))

(defn- event-label [event]
  (case event
    "created" "booking confirmation"
    "status_changed" "status update"
    (str event)))

(defn render-booking-email
  [{:keys [event customerName serviceType carModel status previousStatus bookingId estimatedCost currency]}]
  (let [name (or customerName "Customer")
        service (or serviceType "service")
        model (or carModel "your vehicle")
        booking-ref (when bookingId (str " #" bookingId))
        cost (or estimatedCost "N/A")
        curr (or currency "EUR")
        subject (case event
                  "created"
                  (str "Aston Martin ASO – booking confirmed" booking-ref)
                  "status_changed"
                  (str "Aston Martin ASO – booking status: " status booking-ref)
                  (str "Aston Martin ASO – " (event-label event) booking-ref))
        text-body (case event
                    "created"
                    (str "Hello " name ",\n\n"
                         "Your " service " appointment for " model " has been scheduled.\n"
                         "Current status: " status ".\n\n"
                         "Estimated cost: " cost " " curr ".\n\n"
                         "Thank you for choosing Aston Martin ASO Service.")
                    "status_changed"
                    (str "Hello " name ",\n\n"
                         "Your " service " appointment for " model " has been updated.\n"
                         "Previous status: " previousStatus ".\n"
                         "New status: " status ".\n\n"
                         "Estimated cost: " cost " " curr ".\n\n"
                         "Thank you for choosing Aston Martin ASO Service.")
                    (str "Hello " name ",\n\n"
                         "Update regarding your " service " appointment for " model ".\n"
                         "Status: " status ".\n"
                         "Estimated cost: " cost " " curr ".\n"))
        html-body (str "<html><body>"
                       "<p>Hello " (str/replace name "<" "&lt;") ",</p>"
                       (case event
                         "created"
                         (str "<p>Your <strong>" service "</strong> appointment for "
                              model " has been scheduled.</p>"
                              "<p>Current status: <strong>" status "</strong>.</p>"
                              "<p>Estimated cost: <strong>" cost " " curr "</strong>.</p>")
                         "status_changed"
                         (str "<p>Your <strong>" service "</strong> appointment for "
                              model " has been updated.</p>"
                              "<p>Previous status: " previousStatus "<br/>"
                              "New status: <strong>" status "</strong>.</p>"
                              "<p>Estimated cost: <strong>" cost " " curr "</strong>.</p>")
                         (str "<p>Update regarding your <strong>" service "</strong> appointment.</p>"
                              "<p>Status: <strong>" status "</strong>.</p>"
                              "<p>Estimated cost: <strong>" cost " " curr "</strong>.</p>"))
                       "<p>Thank you for choosing Aston Martin ASO Service.</p>"
                       "</body></html>")]
    {:subject subject
     :textBody text-body
     :htmlBody html-body}))
