(ns aso.email.server
    (:require [aso.email.customer-renderer :as customer-renderer]
              [aso.email.renderer :as renderer]
              [cheshire.core :as json]
              [ring.adapter.jetty :as jetty]
              [ring.util.response :as response])
    (:gen-class))

(defn- render-json [rendered]
  (-> (response/response (json/generate-string rendered))
      (response/content-type "application/json")))

(defn- render-handler [request render-fn]
  (try
    (let [body (-> request :body slurp (json/parse-string true))
          rendered (render-fn body)]
      (render-json rendered))
    (catch Exception e
      (-> (response/response (json/generate-string {:error (.getMessage e)}))
          (response/status 400)
          (response/content-type "application/json")))))

;; Two render endpoints called by the Spring Boot EmailRendererClient.
(defn app [request]
  (case [(:request-method request) (:uri request)]
        [:post "/render/booking-email"] (render-handler request renderer/render-booking-email)
        [:post "/render/customer-email"] (render-handler request customer-renderer/render-customer-email)
        (response/not-found "Not found")))

(defn -main [& _]
  (let [port (or (some-> (System/getenv "PORT") Integer/parseInt) 3000)]
    (println "Email renderer listening on port" port)
    ;; #'app (var) allows REPL reload of the handler without restarting Jetty.
    (jetty/run-jetty #'app {:port port :join? true})))
