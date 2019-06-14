(ns nrepl.proxy
  (:require [net :as net]
            [nrepl.bencode :refer [encode decode]]
            [clojure.pprint :refer [pprint]]))

(defn proxy-to [target direction]
  (fn [data]
    (pprint (assoc (first (decode (.toString data "utf8")))
                   :direction direction))
    (.write target data)))

(defn handler [server target]
  (let [client (net/Socket.)]
    (.connect
     client
     (:port target)
     (fn []
       (.on server "data" (proxy-to client :request))
       (.on client "data" (proxy-to server :response))))))

(defn start-proxy [source target]
  (let [server (net/createServer #(handler % target))]
    (.listen server (:port source) #(println "proxy started"))))

(defn -main [& args]
  (if (zero? (count args))
    (println "Please supply destination port.")
    (let [port (js/parseInt (first args))]
      (println (str "proxying nrepl 4001 -> " port))
      (start-proxy {:port 4001} {:port port}))))

