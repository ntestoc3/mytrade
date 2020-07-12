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

(def api-server "http://www.clontr.club:6688/")

(defn get-all-funds
  "获取所有基金代码"
  []
  (let [result (async/promise-chan)]
    (GET (str api-server "/all_funds.json")
        {:response-format :json
         :keywords? true
         :handler #(async/put! %1)})
    result))

(defn get-fund-history
  [code]
  (let [result (async/promise-chan)]
    (GET (str api-server "/" code ".json")
        {:response-format :json
         :keywords? true
         :handler #(async/put! %1)})
    result))
