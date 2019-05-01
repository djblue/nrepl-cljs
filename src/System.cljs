(ns System)

(defn getProperty [property]
  (case property
    "path.separator" ":"
    "java.class.path" ""
    nil))
