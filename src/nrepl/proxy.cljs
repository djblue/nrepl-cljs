(ns nrepl.proxy
  (:require [net :as net]
            [nrepl.bencode :refer [encode decode-all]]
            [clojure.pprint :refer [pprint]]))

(defn proxy-to [target direction]
  (let [state (atom nil)]
    (fn [data]
      (.write target data)
      (let [data (if (nil? @state)
                   data
                   (js/Buffer.concat (clj->js [@state data])))
            [reqs data] (decode-all data :keywordize-keys true)]
        (doseq [req reqs]
          (pprint (assoc req :direction direction)))
        (reset! state data)))))

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

