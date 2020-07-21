(ns mytrade.data
  (:require [mytrade.cache :as cache]
            [mytrade.api :as api]
            [com.wsscode.async.async-cljs :as wa :refer [go-promise <?]]
            [cljs.core.async :as async :refer [go <!]]
            [oops.core :refer [gget gset! gcall oget oset! ocall oapply ocall! oapply!
                               gget+ gset!+ oget+ oset!+ ocall+ oapply+ ocall!+ oapply!+]]
            [taoensso.timbre :as timbre
             :refer-macros [log  trace  debug  info  warn  error  fatal  report
                            logf tracef debugf infof warnf errorf fatalf reportf
                            spy get-env]]))


;; 最大保存100条历史
(defonce C (cache/localstore-cache :threshold 100))

(defn- with-retry
  [tip f]
  (fn []
    (wa/pulling-retry {::wa/done? (comp not :failure)
                       ::wa/retry-ms 30000
                       ::wa/timeout 300000}
                      (info "get " tip " data...")
                      (f))))

(defn get-all
  []
  (->> (with-retry "all" api/get-all-funds)
       (cache/async-lookup-or-miss C :all)))

(defn get-fund
  [code]
  (->> (with-retry code #(api/get-fund-info code))
       (cache/async-lookup-or-miss C code)))

(defn get-slopes
  []
  (->> (with-retry "slopes" api/get-code-slopes)
       (cache/async-lookup-or-miss C :slopes)))
