(ns nrepl.client
  (:require [nrepl.bencode :refer [encode decode]]))

(defn- on-message [client data]
  (let [messages (:messages client)
        [message data] (decode data :keywordize-keys true)
        id (:id message)]
    (swap! messages update id #(conj % message))
    (when-not (zero? (.-length data))
      (recur client data))))

(defn connect [& {:keys [port]}]
  (js/Promise.
   (fn [resolve reject]
     (let [net (js/require "net")
           socket (.Socket net) messages (atom {})
           client {:socket socket :messages messages}]
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

