(ns aso.email.customer-renderer
  "Renders welcome, vehicle added, and vehicle removed emails."
  (:require [clojure.string :as str]))

(defn- spec-line [label value]
  (when (and value (not (str/blank? (str value))))
    (str label ": " value)))

(defn- vehicle-spec-lines
  [{:keys [carModel modelLine productionYear engine power transmission drivetrain vin]}]
  (let [model (or modelLine carModel)] ;; prefer short model line (e.g. DBX) in emails
    (vec (remove nil?
                 [(spec-line "Model" model)
                  (spec-line "Production year" productionYear)
                  (spec-line "Engine" engine)
                  (spec-line "Power" power)
                  (spec-line "Transmission" transmission)
                  (spec-line "Drivetrain" drivetrain)
                  (spec-line "VIN" vin)]))))

(defn render-customer-email
  [{:keys [event customerName carModel vin modelLine productionYear engine power transmission drivetrain]}]
  (let [name (or customerName "Customer")
        spec-lines (vehicle-spec-lines
                    {:carModel carModel
                     :modelLine modelLine
                     :productionYear productionYear
                     :engine engine
                     :power power
                     :transmission transmission
                     :drivetrain drivetrain
                     :vin vin})]
    (case event
      "customer_registered"
      {:subject "Aston Martin ASO – welcome"
       :textBody (str "Hello " name ",\n\n"
                      "Your Aston Martin ASO Service account has been registered.\n\n"
                      "You can now add vehicles and submit service requests through our workshop.\n\n"
                      "Thank you for choosing Aston Martin ASO Service.")
       :htmlBody (str "<html><body>"
                      "<p>Hello " (str/replace name "<" "&lt;") ",</p>"
                      "<p>Your <strong>Aston Martin ASO Service</strong> account has been registered.</p>"
                      "<p>You can now add vehicles and submit service requests through our workshop.</p>"
                      "<p>Thank you for choosing Aston Martin ASO Service.</p>"
                      "</body></html>")}

      "vehicle_added"
      {:subject "Aston Martin ASO – vehicle added"
       :textBody (str "Hello " name ",\n\n"
                      "A vehicle has been added to your account:\n"
                      (str/join "\n" spec-lines)
                      "\n\nThank you for choosing Aston Martin ASO Service.")
       :htmlBody (str "<html><body>"
                      "<p>Hello " (str/replace name "<" "&lt;") ",</p>"
                      "<p>A vehicle has been added to your account:</p>"
                      "<p>"
                      (str/join "<br/>"
                                (map #(str/replace % "<" "&lt;") spec-lines))
                      "</p>"
                      "<p>Thank you for choosing Aston Martin ASO Service.</p>"
                      "</body></html>")}

      "vehicle_removed"
      {:subject "Aston Martin ASO – vehicle removed"
       :textBody (str "Hello " name ",\n\n"
                      "A vehicle has been removed from your account:\n"
                      (str/join "\n" spec-lines)
                      "\n\nYour past service records are kept on file.\n\n"
                      "Thank you for choosing Aston Martin ASO Service.")
       :htmlBody (str "<html><body>"
                      "<p>Hello " (str/replace name "<" "&lt;") ",</p>"
                      "<p>A vehicle has been removed from your account:</p>"
                      "<p>"
                      (str/join "<br/>"
                                (map #(str/replace % "<" "&lt;") spec-lines))
                      "</p>"
                      "<p>Your past service records are kept on file.</p>"
                      "<p>Thank you for choosing Aston Martin ASO Service.</p>"
                      "</body></html>")}

      {:subject "Aston Martin ASO – notification"
       :textBody (str "Hello " name ",\n\nYou have a new update from Aston Martin ASO Service.")
       :htmlBody (str "<html><body><p>Hello " (str/replace name "<" "&lt;") ",</p>"
                      "<p>You have a new update from Aston Martin ASO Service.</p>"
                      "</body></html>")})))
