(ns nrepl.client
  (:require [nrepl.bencode :refer [encode decode-all]]))

(defn- on-message [client data]
  (let [data            (js/Buffer.concat #js [@(:data client) data])
        [messages data] (decode-all data :keywordize-keys true)]
    (reset! (:data client) data)
    (doseq [message messages]
      (swap! (:messages client) update (:id message) conj message))))

(def empty-buffer (js/Buffer.from ""))

(defn connect [& {:keys [port]}]
  (js/Promise.
   (fn [resolve reject]
     (let [net (js/require "net")
           socket (.Socket net) messages (atom {}) data (atom empty-buffer)
           client {:socket socket :messages messages :data data}]
       (.connect socket port #(resolve client))
       (.on socket "data" #(on-message client %))))))

(defn message [client message]
  (js/Promise.
   (fn [resolve reject]
     (let [id (str (random-uuid)) messages (:messages client)]
       (swap! messages assoc id [])
       (add-watch messages
                  id
                  (fn []
                    (when-let [message (get @messages id)]
                      (when (some #((set (:status %)) "done") message)
                        (remove-watch messages id)
                        (swap! messages dissoc id)
                        (resolve message)))))
       (.write (:socket client) (encode (assoc message :id id)))))))

(defn close [client]
  (.end (:socket client)))

(comment
  (def c (atom nil))
  (-> @c)
  (.then (connect :port 7888)
         #(reset! c %))
  (:messages @c)
  (message @c {:op "eval" :code "{}"}))

