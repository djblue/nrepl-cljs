(ns nrepl.client
  (:require [net :as net]
            [uuid :as uuid]
            [nrepl.bencode :refer [encode decode]]))

(defn- on-message [client data]
  (let [messages (:messages client)
        message (first (decode (.toString data "utf8")))
        id (get message "id")]
    (swap! messages assoc id message)))

(defn connect [& {:keys [port]}]
  (js/Promise.
   (fn [resolve reject]
     (let [socket (net/Socket.) messages (atom {})
           client {:socket socket :messages messages}]
       (.connect socket port #(resolve client))
       (.on socket "data" #(on-message client %))))))

(defn promise? [v] (instance? js/Promise v))

(defn message [client message]
  (js/Promise.
   (fn [resolve reject]
     (let [id (str (uuid/v4)) messages (:messages client)]
       (add-watch messages
                  id
                  #(when-let [message (get @messages id)]
                     (remove-watch messages id)
                     (swap! messages dissoc id)
                     (resolve message)))
       (.write (:socket client) (encode (assoc message "id" id)))))))

(comment
  (def c (atom nil))
  (-> @c)
  (.then (connect :port 3000)
         #(reset! c %))
  (message
    @c
    {"op" "eval"
     "code" "{}"}))

