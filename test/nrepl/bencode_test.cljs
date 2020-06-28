(ns nrepl.bencode-test
  (:require [clojure.test :refer [are deftest testing is]]
            [nrepl.bencode :as bencode :refer [read-bencode
                                               write-bencode]]))

(def examples
  [;; netstrings
   "0:"                ""
   "13:Hello, World!"  "Hello, World!"
   "16:Hällö, Würld!"  "Hällö, Würld!"
   "25:Здравей, Свят!" "Здравей, Свят!"

   ;; integers
   "i0e"     0
   "i42e"   42
   "i-42e" -42

   ;; lists
   "le"                    []
   "l6:cheesee"            ["cheese"]
   "l6:cheese3:ham4:eggse" ["cheese" "ham" "eggs"]

   ;; maps
   "de"            {}
   "d3:ham4:eggse" {"ham" "eggs"}

   ;; nested
   "l6:cheesei42ed3:ham4:eggsee" ["cheese" 42 {"ham" "eggs"}]
   "d6:cheesei42e3:haml4:eggsee" {"cheese" 42 "ham" ["eggs"]}])

(deftest test-examples
  (doseq [[x y] (partition 2 examples)]
    (testing y
      (testing "read-bencode"
        (is (= (read-bencode (js/Buffer.from x)) y)))
      (testing "write-bencode"
        (is (= (write-bencode y) x))))))

(deftest test-clojure-examples
  (are [x y] (= (write-bencode x) y)
    :foo           "3:foo"
    :foo/bar       "7:foo/bar"
    'foo           "3:foo"
    'foo/bar       "7:foo/bar"
    {:ham "eggs"}  "d3:ham4:eggse"
    {'ham "eggs"}  "d3:ham4:eggse"
    {:h/am "eggs"} "d4:h/am4:eggse"))

