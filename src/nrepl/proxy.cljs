(ns nrepl.proxy
  (:require [net :as net]
            [nrepl.bencode :refer [encode decode]]
            [clojure.pprint :refer [pprint]]))

(defn server->client [server client data]
  (pprint (assoc (first (decode (.toString data "utf8")))
                 :direction :server->client))
  (.write client data))

(defn client->server [server client data]
  (pprint (assoc (first (decode (.toString data "utf8")))
                 :direction :client->server))
  (.write server data))

(defn handler [server target]
  (let [client (net/Socket.)]
    (.connect
     client
     (:port target)
     (fn []
       (do
         (.on server "data" #(server->client server client %))
         (.on client "data" #(client->server server client %)))))))

(defn start-proxy [source target]
  (let [server (net/createServer #(handler % target))]
    (.listen server (:port source))))

(comment
  (start-proxy {:port 3001} {:port 37433}))


