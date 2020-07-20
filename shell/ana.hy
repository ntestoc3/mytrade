
(require [hy.extra.anaphoric [*]])
(import [numpy :as np])
;; (import [matplotlib.pyplot :as plt])
(import json)
(import glob)
(import [pathlib [Path]])

(eval-when-compile
  (defn parse-colon [sym]
    (->> (.split (str sym) ":")
         (map (fn [x]
                (if (empty? x)
                    None
                    (int x))))
         list))

  (defn parse-indexing [sym]
    (cond
      [(in ":" (str sym)) `(slice ~@(parse-colon sym))]
      [(in "..." (str sym)) 'Ellipsis]
      [True sym])))

(defmacro nget [ar &rest keys]
  `(get ~ar (, ~@(map parse-indexing keys))))

(defn read-data
  [fname]
  (with [f (open fname)]
     (json.load f)))

(defn calc-poly
  [data]
  (when (> (len data) 0)
    (setv d (->> data
                 (remove #%(none? (of %1 1)))
                 list
                 (np.array)))
    (np.polyfit (nget d ... 0)
                (nget d ... 1)
                1)))

(defn get-ac-worth-slope
  [info]
  (print "get slope for:" (of info "code"))
  (-> (of info "Data_ACWorthTrend")
      calc-poly
      first))

(with [f (open "datas/slopes.json" "w")]
  (-> (->> (glob.glob "datas/[0-9]*.json")
           (map read-data)
           (remove #%(-> (of %1 "Data_ACWorthTrend")
                         len
                         zero?))
           (map (fn [info]
                  {"code" (of info "code")
                   "slope" (get-ac-worth-slope info)}))
           (sorted :key #%(of %1 "slope"))
           reversed
           list)
      (json.dump f)))
