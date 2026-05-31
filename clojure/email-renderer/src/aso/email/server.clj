(ns aso.email.server
    (:require [aso.email.renderer :as renderer]
              [cheshire.core :as json]
              [ring.adapter.jetty :as jetty]
              [ring.util.response :as response])
    (:gen-class))

(defn- render-handler [request]
  (try
    (let [body (-> request :body slurp (json/parse-string true))
          rendered (renderer/render-booking-email body)]
      (-> (response/response (json/generate-string rendered))
          (response/content-type "application/json")))
    (catch Exception e
      (-> (response/response (json/generate-string {:error (.getMessage e)}))
          (response/status 400)
          (response/content-type "application/json")))))

(defn app [request]
  (case [(:request-method request) (:uri request)]
        [:post "/render/booking-email"] (render-handler request)
        (response/not-found "Not found")))

(defn -main [& _]
  (let [port (or (some-> (System/getenv "PORT") Integer/parseInt) 3000)]
    (println "Email renderer listening on port" port)
    (jetty/run-jetty #'app {:port port :join? true})))
