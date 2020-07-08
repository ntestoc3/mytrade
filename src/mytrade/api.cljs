(ns mytrade.api
  (:require [ajax.core :refer [GET POST]]
            [mytrade.db :as db]
            [cljs-time.core :as time]
            [cljs.core.async :as async :refer [go <!]]
            [goog.string :as gstring]
            [cljs-time.format :as timef]
            [taoensso.timbre :as timbre
             :refer-macros [log  trace  debug  info  warn  error  fatal  report
                            logf tracef debugf infof warnf errorf fatalf reportf
                            spy get-env]]))

(defn get-r
  [code]
  ((-> (str "\"use strict\";" code "; return (r);")
       (js/Function))))

(defn get-all-funds
  "获取所有基金代码"
  []
  (GET "http://fund.eastmoney.com/js/fundcode_search.js"
      {:handler #(->> (get-r %)
                      (map (fn [[code short-name name type full-name]]
                             {:db/id (js/parseInt code)
                              :code code
                              :short-name short-name
                              :name name
                              :type type
                              :full-name full-name}))
                      (db/transact!))}))

(def time-formatter (timef/formatter "yyyyMMddHHmmss"))

(defn curr-times
  []
  (timef/unparse time-formatter (time/time-now)))

(def fund-info (atom {}))

(defn get-fund-info
  [code]
  (->> ((-> (str "\"use strict\";" code "; return [Data_grandTotal, Data_ACWorthTrend];")
            (js/Function)))
       (zipmap [:grand-total :ac-worth-trend])))

(defn get-fund-history
  [code]
  (GET (gstring/format
        "http://fund.eastmoney.com/pingzhongdata/%s.js?v=%s"
        code
        (curr-times))
      {:handler #(->> (get-fund-info %)
                      (swap! fund-info assoc code))}))
