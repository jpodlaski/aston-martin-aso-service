(ns aso.email.layout
  "Shared branding and document shell for ASO notification emails."
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.util Base64]))

(def workshop-address "Sportowa 31, Łódź, Poland")

(def brand-name "Aston Martin ASO Service")

(def workshop-hours "06:00–20:00")

;; Site / wallpaper palette: graphite + racing green (#00393d)
(def ^:private ink "#0a0c0e")
(def ^:private ink-soft "#14181c")
(def ^:private header-bg "#00393d")
(def ^:private snow "#f3f4f2")
(def ^:private text "#b8bbb6")
(def ^:private muted "#8a8e89")
(def ^:private accent-bright "#2f6b50")
(def ^:private accent-soft "#8fbfa5")
(def ^:private accent-border "rgba(47,107,80,0.65)")

(def ^:private logo-data-uri
  (delay
    (with-open [in (io/input-stream (io/resource "email/aston-martin-logo.png"))]
      (let [bytes (.readAllBytes in)
            b64 (.encodeToString (Base64/getEncoder) bytes)]
        (str "data:image/png;base64," b64)))))

(defn escape-html [value]
  (-> (str (or value ""))
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- blank-line? [x]
  (or (nil? x) (str/blank? (str x))))
(defn- keep-non-blank-lines [lines acc]
  (if (empty? lines)
    (reverse acc)
    (let [line (first lines)]
      (if (blank-line? line)
        (recur (rest lines) acc)
        (recur (rest lines) (conj acc line))))))
(defn- join-fragments [fragments]
  (reduce str "" fragments))
(defn- text-lines [lines]
  (-> (keep-non-blank-lines lines [])
      (str/join "\n")))

(defn wrap-text
  "Plain-text email body with greeting, content lines, and branded closing."
  [customer-name body-lines]
  (let [name (or customer-name "Customer")
        body (if (string? body-lines)
               body-lines
               (text-lines body-lines))]
    (str "Dear " name ",\n\n"
         body
         "\n\n"
         "Service location: " workshop-address "\n\n"
         "Kind regards,\n"
         brand-name)))

(defn- detail-rows-html [pairs]
  (->> pairs
       (remove (fn [[_ v]] (or (nil? v) (str/blank? (str v)))))
       (map (fn [[label value]]
              (str "<tr>"
                   "<td style=\"padding:6px 12px 6px 0;color:" accent-soft ";font-size:12px;"
                   "letter-spacing:0.08em;text-transform:uppercase;vertical-align:top;"
                   "white-space:nowrap;\">"
                   (escape-html label)
                   "</td>"
                   "<td style=\"padding:6px 0;color:" snow ";font-size:14px;vertical-align:top;\">"
                   (escape-html (str value))
                   "</td>"
                   "</tr>")))
       (join-fragments)))

(defn details-table
  "Optional labeled detail block for HTML emails."
  [pairs]
  (let [rows (detail-rows-html pairs)]
    (when-not (str/blank? rows)
      (str "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" "
           "style=\"width:100%;margin:18px 0;border-collapse:collapse;\">"
           rows
           "</table>"))))

(defn details-text
  "Labeled detail lines for plain-text emails."
  [pairs]
  (->> pairs
       (remove (fn [[_ v]] (or (nil? v) (str/blank? (str v)))))
       (map (fn [[label value]] (str label ": " value)))
       (str/join "\n")))

(defn cta-html
  "Primary button + fallback paste link."
  [label url]
  (when (and url (not (str/blank? url)))
    (let [safe-url (escape-html url)
          safe-label (escape-html label)]
      (str "<p style=\"margin:24px 0 8px;\">"
           "<a href=\"" safe-url "\" "
           "style=\"display:inline-block;padding:12px 18px;background:" accent-bright ";"
           "color:" snow ";text-decoration:none;font-size:13px;font-weight:600;"
           "letter-spacing:0.08em;text-transform:uppercase;border:1px solid " accent-border ";\">"
           safe-label
           "</a></p>"
           "<p style=\"margin:0 0 16px;color:" muted ";font-size:12px;line-height:1.5;\">"
           "Or paste this link into your browser:<br/>"
           "<span style=\"color:" text ";word-break:break-all;\">" safe-url "</span>"
           "</p>"))))

(defn wrap-html
  "Full HTML document with branded header, body HTML fragments, and footer."
  [customer-name body-html]
  (let [safe-name (escape-html (or customer-name "Customer"))]
    (str "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>"
         "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
         "</head>"
         "<body style=\"margin:0;padding:0;background:" ink ";"
         "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;"
         "color:" snow ";\">"
         "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
         "style=\"background:" ink ";padding:24px 12px;\">"
         "<tr><td align=\"center\">"
         "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
         "style=\"max-width:560px;background:" ink-soft ";border:1px solid " accent-border ";\">"
         ;; Header: cropped logo only (no ASO Service label)
         "<tr><td align=\"center\" style=\"padding:22px 28px;background:" header-bg ";"
         "border-bottom:1px solid " accent-border ";\">"
         "<img src=\"" @logo-data-uri "\" width=\"280\" alt=\"Aston Martin\" "
         "style=\"display:block;margin:0 auto;width:280px;max-width:85%;height:auto;border:0;\"/>"
         "</td></tr>"
         "<tr><td style=\"padding:28px;\">"
         "<p style=\"margin:0 0 16px;font-size:15px;line-height:1.55;color:" snow ";\">Dear "
         safe-name ",</p>"
         body-html
         "</td></tr>"
         "<tr><td style=\"padding:18px 28px 24px;border-top:1px solid " accent-border ";"
         "background:" header-bg ";\">"
         "<p style=\"margin:0 0 6px;font-size:12px;letter-spacing:0.1em;text-transform:uppercase;"
         "color:" accent-soft ";\">Service location</p>"
         "<p style=\"margin:0 0 14px;font-size:14px;color:" snow ";\">"
         (escape-html workshop-address) "</p>"
         "<p style=\"margin:0;font-size:13px;line-height:1.5;color:" muted ";\">"
         "Kind regards,<br/>"
         "<span style=\"color:" snow ";\">" (escape-html brand-name) "</span>"
         "</p>"
         "</td></tr>"
         "</table>"
         "</td></tr></table>"
         "</body></html>")))

(defn p
  "Paragraph fragment for HTML body content."
  [text]
  (str "<p style=\"margin:0 0 14px;font-size:15px;line-height:1.55;color:" snow ";\">"
       (escape-html text)
       "</p>"))

(defn p-html
  "Paragraph that already contains trusted escaped HTML (e.g. strong)."
  [html]
  (str "<p style=\"margin:0 0 14px;font-size:15px;line-height:1.55;color:" snow ";\">"
       html
       "</p>"))
