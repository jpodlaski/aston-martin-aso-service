(ns aso.email.customer-renderer
  "Renders welcome, verification, password-reset, and vehicle emails."
  (:require [aso.email.layout :as layout]
            [clojure.string :as str]))

(defn- spec-line [label value]
  (when (and value (not (str/blank? (str value))))
    [label value]))

(defn- vehicle-spec-pairs
  [{:keys [carModel modelLine productionYear engine power transmission drivetrain vin]}]
  (let [model (or modelLine carModel)]
    (vec (remove nil?
                 [(spec-line "Model" model)
                  (spec-line "Production year" productionYear)
                  (spec-line "Engine" engine)
                  (spec-line "Power" power)
                  (spec-line "Transmission" transmission)
                  (spec-line "Drivetrain" drivetrain)
                  (spec-line "VIN" vin)]))))

(defn- action-email
  [customer-name subject intro cta-label action-url & {:keys [extra-lines]}]
  (let [name (or customer-name "Customer")
        text-extra (when (seq extra-lines)
                     (str "\n" (str/join "\n" extra-lines)))
        text-body (layout/wrap-text
                   name
                   (str intro
                        (when action-url
                          (str "\n\n" cta-label ":\n" action-url))
                        text-extra))
        html-body (layout/wrap-html
                   name
                   (str (layout/p intro)
                        (layout/cta-html cta-label action-url)
                        (when (seq extra-lines)
                          (str/join "" (map layout/p extra-lines)))))]
    {:subject subject
     :textBody text-body
     :htmlBody html-body}))

(defn render-customer-email
  [{:keys [event customerName carModel vin modelLine productionYear engine power
           transmission drivetrain actionUrl]}]
  (let [name (or customerName "Customer")
        spec-pairs (vehicle-spec-pairs
                    {:carModel carModel
                     :modelLine modelLine
                     :productionYear productionYear
                     :engine engine
                     :power power
                     :transmission transmission
                     :drivetrain drivetrain
                     :vin vin})
        spec-text (layout/details-text spec-pairs)
        spec-html (layout/details-table spec-pairs)]
    (case event
      "customer_registered"
      (action-email
       name
       "Aston Martin ASO – welcome"
       (str "Your " layout/brand-name " account has been registered. "
            "Please verify your email before signing in.")
       "Verify email"
       actionUrl
       :extra-lines ["This link expires after 24 hours."])

      "email_verification"
      (action-email
       name
       "Aston Martin ASO – verify your email"
       "Please verify your email address to activate your account."
       "Verify email"
       (or actionUrl "")
       :extra-lines ["This link expires after 24 hours."])

      "password_reset"
      (action-email
       name
       "Aston Martin ASO – reset your password"
       "We received a request to reset your password. Open the link below to choose a new one."
       "Reset password"
       (or actionUrl "")
       :extra-lines ["If you did not request this, you can ignore this email."
                     "This link expires after 1 hour."])

      "password_changed"
      {:subject "Aston Martin ASO – password changed"
       :textBody (layout/wrap-text
                  name
                  (str "Your " layout/brand-name " password was changed successfully.\n\n"
                       "If you did not make this change, reset your password immediately "
                       "and contact the workshop."))
       :htmlBody (layout/wrap-html
                  name
                  (str (layout/p (str "Your " layout/brand-name
                                      " password was changed successfully."))
                       (layout/p (str "If you did not make this change, reset your password "
                                      "immediately and contact the workshop."))))}

      "account_deletion"
      (action-email
       name
       "Aston Martin ASO – confirm account deletion"
       (str "We received a request to delete your " layout/brand-name " account. "
            "Open the link below to confirm deletion.")
       "Confirm account deletion"
       (or actionUrl "")
       :extra-lines ["If you did not request this, you can ignore this email — your account will stay active."
                     "This link expires after 1 hour."])

      "account_deleted"
      {:subject "Aston Martin ASO – account deleted"
       :textBody (layout/wrap-text
                  name
                  (str "Your " layout/brand-name " account has been deleted.\n\n"
                       "Past workshop records may be retained by the service centre as required."))
       :htmlBody (layout/wrap-html
                  name
                  (str (layout/p (str "Your " layout/brand-name " account has been deleted."))
                       (layout/p "Past workshop records may be retained by the service centre as required.")))}

      "vehicle_added"
      {:subject "Aston Martin ASO – vehicle added"
       :textBody (layout/wrap-text
                  name
                  (str "A vehicle has been added to your account.\n\n" spec-text))
       :htmlBody (layout/wrap-html
                  name
                  (str (layout/p "A vehicle has been added to your account.")
                       (or spec-html "")))}

      "vehicle_removed"
      {:subject "Aston Martin ASO – vehicle removed"
       :textBody (layout/wrap-text
                  name
                  (str "A vehicle has been removed from your account.\n\n"
                       spec-text
                       "\n\nYour past service records are kept on file."))
       :htmlBody (layout/wrap-html
                  name
                  (str (layout/p "A vehicle has been removed from your account.")
                       (or spec-html "")
                       (layout/p "Your past service records are kept on file.")))}

      {:subject "Aston Martin ASO – notification"
       :textBody (layout/wrap-text
                  name
                  (str "You have a new update from " layout/brand-name "."))
       :htmlBody (layout/wrap-html
                  name
                  (layout/p (str "You have a new update from " layout/brand-name ".")))})))
