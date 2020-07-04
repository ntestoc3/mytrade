(ns hooks.sass
  (:require [clojure.java.shell :refer [sh]])
  )

(defn watch
  {:shadow.build/stage :configure}
  ([build-state]
   (watch build-state {}))
  ([build-state options]
   (prn "run sass watch...")
   (print (:out (sh "sh" "-c" "yarn watch:sass &")))
   (prn "sass watch ok!")
   build-state))

