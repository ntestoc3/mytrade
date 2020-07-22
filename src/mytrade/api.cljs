(ns mytrade.api
  (:require [ajax.core :refer [GET POST]]
            [cljs.core.async :as async :refer [go <!]]
            [com.wsscode.async.async-cljs :as wa :refer [go-promise <? <?maybe]]
            [goog.string :as gstring]
            [taoensso.timbre :as timbre
             :refer-macros [log  trace  debug  info  warn  error  fatal  report
                            logf tracef debugf infof warnf errorf fatalf reportf
                            spy get-env]]))

(def api-server "http://www.clontr.club:6688/")

(defn- get-data
  [data-name]
  (let [result (async/promise-chan)]
    (GET (str api-server "/" data-name ".json")
        {:response-format :json
         :keywords? true
         :error-handler (fn [err]
                          (error err)
                          (async/put! result err))
         :handler #(async/put! result %1)})
    result))

(def get-all-funds "获取所有基金代码" #(get-data "all_funds"))
(def get-fund-info "获取指定基金代码的信息" get-data)
