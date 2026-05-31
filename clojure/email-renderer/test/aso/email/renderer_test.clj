(ns aso.email.renderer-test
  (:require [aso.email.renderer :refer [render-booking-email]]
            [clojure.test :refer [deftest is testing]]))

(deftest render-created-email
  (testing "created event"
    (let [result (render-booking-email
                  {:event "created"
                   :customerName "Jane Doe"
                   :serviceType "Oil change"
                   :carModel "Toyota Corolla"
                   :status "SCHEDULED"
                   :bookingId 42})]
      (is (str/includes? (:subject result) "confirmed"))
      (is (str/includes? (:textBody result) "Jane Doe"))
      (is (str/includes? (:textBody result) "Oil change"))
      (is (str/includes? (:htmlBody result) "Toyota Corolla")))))

(deftest render-status-changed-email
  (testing "status_changed event"
    (let [result (render-booking-email
                  {:event "status_changed"
                   :customerName "Jane Doe"
                   :serviceType "Oil change"
                   :carModel "Toyota Corolla"
                   :status "IN_PROGRESS"
                   :previousStatus "SCHEDULED"
                   :bookingId 42})]
      (is (str/includes? (:subject result) "IN_PROGRESS"))
      (is (str/includes? (:textBody result) "Previous status: SCHEDULED"))
      (is (str/includes? (:textBody result) "New status: IN_PROGRESS")))))
