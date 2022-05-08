(ns data
  (:require [clojure.java.io :as io]))

(require '[babashka.fs :as fs])
(require '[cheshire.core :as json])
(require '[babashka.deps :as deps])
(deps/add-deps '{:deps {camel-snake-kebab/camel-snake-kebab {:mvn/version "0.4.2"}}})
(require '[clojure.tools.logging :as log])
(require '[camel-snake-kebab.core :as csk])


(def all-data (->> (fs/glob  "datas" "*.json")
                   (map #(let [data (json/decode (slurp (str %1)) csk/->kebab-case-keyword)]
                           (-> (:data-grand-total data)
                               first
                               (assoc :code (:code data)))))))


(def big-data (->> (filter identity all-data)
                   (filter #(try (and (pos? (count (:data %)))
                                      (> (or (some->> (map second (:data %))
                                                      (apply max))
                                             0)
                                         1000))
                                 (catch Exception e
                                   (log/error :data %))))))


(def slopes (json/decode (slurp "datas/slopes.json") keyword))

(def all (json/decode (slurp "datas/all_funds.json") keyword))

(defn get-slope
  [code]
  (or (-> (filter #(= code (:code %)) slopes)
          first
          :slope)
      0))

(as-> all $
  (map #(assoc % :slope (get-slope (:code %)) ) $)
  (json/encode $ {:pretty true})
  (spit "datas/all_funds.json" $))

(take 30 (reverse (sort-by :slope slopes)))
