(ns nrepl.bencode)

(defn- index-of [s c] (.indexOf s c))

(defn decode [data]
  (case (first data)
    "i"
    (let [data (subs data 1)
          i (index-of data "e")]
      [(js/parseInt (subs data 0 i))
       (subs data (inc i))])
    "l"
    (let [data (subs data 1)]
      (loop [data data v []]
        (if (= (first data) "e")
          [v (subs data 1)]
          (let [[value data] (decode data)]
            (recur data (conj v value))))))
    "d"
    (let [data (subs data 1)]
      (loop [data data m {}]
        (if (= (first data) "e")
          [m (subs data 1)]
          (let [[k data] (decode data)
                [v data] (decode data)]
            (recur data (assoc m k v))))))
    (let [i (index-of data ":")
          n (js/parseInt (subs data 0 i))
          data (subs data (inc i))]
      [(subs data 0 n) (subs data n)])))

(defn read-bencode [string] (first (decode string)))

(defn encode [data]
  (cond
    (string? data)
    (str (count data) ":" data)
    (or (keyword? data)
        (symbol? data))
    (recur (str
             (if-let [n (namespace data)]
               (str n "/"))
             (name data)))
    (number? data)
    (str "i" data "e")
    (or (vector? data) (nil? data))
    (str "l" (apply str (map encode data)) "e")
    (map? data)
    (str "d" (->> data
                  (sort-by first)
                  (map (fn [[k v]]
                         (str (encode k) (encode v))))
                  (apply str))
         "e")))

(defn write-bencode [data] (encode data))
