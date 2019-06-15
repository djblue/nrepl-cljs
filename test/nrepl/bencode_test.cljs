(ns nrepl.bencode-test
  (:require [clojure.test :refer [are deftest is]]
            [nrepl.bencode :as bencode :refer [read-bencode
                                               write-bencode]]))

(deftest test-string-reading
  (are [x y] (= (str (read-bencode (js/Buffer.from x))) y)
    "0:"                ""
    "13:Hello, World!"  "Hello, World!"
    "16:Hällö, Würld!"  "Hällö, Würld!"
    "25:Здравей, Свят!" "Здравей, Свят!"))

(deftest test-integer-reading
  (are [x y] (= (read-bencode x) y)
    "i0e"     0
    "i42e"   42
    "i-42e" -42))

(deftest test-list-reading
  (are [x y] (= (read-bencode x) y)
    "le"                    []
    "l6:cheesee"            ["cheese"]
    "l6:cheese3:ham4:eggse" ["cheese" "ham" "eggs"]))

(deftest test-map-reading
  (are [x y] (= (read-bencode x) y)
    "de"            {}
    "d3:ham4:eggse" {"ham" "eggs"}))

(deftest test-nested-reading
  (are [x y] (= (read-bencode x) y)
    "l6:cheesei42ed3:ham4:eggsee" ["cheese" 42 {"ham" "eggs"}]
    "d6:cheesei42e3:haml4:eggsee" {"cheese" 42 "ham" ["eggs"]}))

(deftest test-integer-writing
  (are [x y] (= (write-bencode x) y)
    0 "i0e"
    42 "i42e"
    -42 "i-42e"))

(deftest test-named-writing
  (are [x y] (= (write-bencode x) y)
    :foo      "3:foo"
    :foo/bar  "7:foo/bar"
    'foo      "3:foo"
    'foo/bar  "7:foo/bar"))

(deftest test-list-writing
  (are [x y] (= (write-bencode x) y)
    nil                     "le"
    []                      "le"
    ["cheese"]              "l6:cheesee"
    ["cheese" "ham" "eggs"] "l6:cheese3:ham4:eggse"))

(deftest test-map-writing
  (are [x y] (= (write-bencode x) y)
    {}             "de"
    {"ham" "eggs"} "d3:ham4:eggse"
    {:ham "eggs"}  "d3:ham4:eggse"
    {'ham "eggs"}  "d3:ham4:eggse"
    {:h/am "eggs"} "d4:h/am4:eggse"))

(deftest test-nested-writing
  (are [x y] (= (write-bencode x) y)
    ["cheese" 42 {"ham" "eggs"}] "l6:cheesei42ed3:ham4:eggsee"
    {"cheese" 42 "ham" ["eggs"]} "d6:cheesei42e3:haml4:eggsee"))

