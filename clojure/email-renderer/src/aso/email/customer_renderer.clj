(ns aso.email.customer-renderer
  "Renders welcome, verification, password-reset, and vehicle emails."
  (:require [clojure.string :as str]))

(defn- escape-html [value]
  (-> (str (or value ""))
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

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
  [{:keys [event customerName carModel vin modelLine productionYear engine power transmission drivetrain actionUrl]}]
  (let [name (or customerName "Customer")
        safe-name (escape-html name)
        safe-url (escape-html actionUrl)
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
                      (if actionUrl
                        (str "Please verify your email by opening this link before signing in:\n"
                             actionUrl
                             "\n\nThis link expires after 24 hours.\n\n")
                        "Please verify your email before signing in.\n\n")
                      "Thank you for choosing Aston Martin ASO Service.")
       :htmlBody (str "<html><body>"
                      "<p>Hello " safe-name ",</p>"
                      "<p>Your <strong>Aston Martin ASO Service</strong> account has been registered.</p>"
                      (if actionUrl
                        (str "<p>Please verify your email before signing in:</p>"
                             "<p><a href=\"" safe-url "\">Verify email</a></p>"
                             "<p>Or paste this link into your browser:<br/>" safe-url "</p>"
                             "<p>This link expires after 24 hours.</p>")
                        "<p>Please verify your email before signing in.</p>")
                      "<p>Thank you for choosing Aston Martin ASO Service.</p>"
                      "</body></html>")}

      "email_verification"
      {:subject "Aston Martin ASO – verify your email"
       :textBody (str "Hello " name ",\n\n"
                      "Please verify your email address by opening this link:\n"
                      (or actionUrl "(missing link)")
                      "\n\nThis link expires after 24 hours.\n\n"
                      "Thank you for choosing Aston Martin ASO Service.")
       :htmlBody (str "<html><body>"
                      "<p>Hello " safe-name ",</p>"
                      "<p>Please verify your email address:</p>"
                      "<p><a href=\"" safe-url "\">Verify email</a></p>"
                      "<p>Or paste this link into your browser:<br/>" safe-url "</p>"
                      "<p>This link expires after 24 hours.</p>"
                      "<p>Thank you for choosing Aston Martin ASO Service.</p>"
                      "</body></html>")}

      "password_reset"
      {:subject "Aston Martin ASO – reset your password"
       :textBody (str "Hello " name ",\n\n"
                      "We received a request to reset your password. Open this link to choose a new one:\n"
                      (or actionUrl "(missing link)")
                      "\n\nIf you did not request this, you can ignore this email.\n"
                      "This link expires after 1 hour.\n\n"
                      "Thank you for choosing Aston Martin ASO Service.")
       :htmlBody (str "<html><body>"
                      "<p>Hello " safe-name ",</p>"
                      "<p>We received a request to reset your password.</p>"
                      "<p><a href=\"" safe-url "\">Reset password</a></p>"
                      "<p>Or paste this link into your browser:<br/>" safe-url "</p>"
                      "<p>If you did not request this, you can ignore this email.</p>"
                      "<p>This link expires after 1 hour.</p>"
                      "<p>Thank you for choosing Aston Martin ASO Service.</p>"
                      "</body></html>")}

      "password_changed"
      {:subject "Aston Martin ASO – password changed"
       :textBody (str "Hello " name ",\n\n"
                      "Your Aston Martin ASO Service password was changed successfully.\n\n"
                      "If you did not make this change, reset your password immediately and contact the workshop.\n\n"
                      "Thank you for choosing Aston Martin ASO Service.")
       :htmlBody (str "<html><body>"
                      "<p>Hello " safe-name ",</p>"
                      "<p>Your <strong>Aston Martin ASO Service</strong> password was changed successfully.</p>"
                      "<p>If you did not make this change, reset your password immediately and contact the workshop.</p>"
                      "<p>Thank you for choosing Aston Martin ASO Service.</p>"
                      "</body></html>")}

      "vehicle_added"
      {:subject "Aston Martin ASO – vehicle added"
       :textBody (str "Hello " name ",\n\n"
                      "A vehicle has been added to your account:\n"
                      (str/join "\n" spec-lines)
                      "\n\nThank you for choosing Aston Martin ASO Service.")
       :htmlBody (str "<html><body>"
                      "<p>Hello " safe-name ",</p>"
                      "<p>A vehicle has been added to your account:</p>"
                      "<p>"
                      (str/join "<br/>" (map escape-html spec-lines))
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
                      "<p>Hello " safe-name ",</p>"
                      "<p>A vehicle has been removed from your account:</p>"
                      "<p>"
                      (str/join "<br/>" (map escape-html spec-lines))
                      "</p>"
                      "<p>Your past service records are kept on file.</p>"
                      "<p>Thank you for choosing Aston Martin ASO Service.</p>"
                      "</body></html>")}

      {:subject "Aston Martin ASO – notification"
       :textBody (str "Hello " name ",\n\nYou have a new update from Aston Martin ASO Service.")
       :htmlBody (str "<html><body><p>Hello " safe-name ",</p>"
                      "<p>You have a new update from Aston Martin ASO Service.</p>"
                      "</body></html>")})))
