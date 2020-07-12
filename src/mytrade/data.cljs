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
                            spy get-env]]
            ))


;; 最大保存100条历史
(defonce C (cache/localstore-cache :threshold 100))

(defn- get-all-data-with-retry
  []
  (wa/pulling-retry {::wa/done? (comp not :failure)
                     ::wa/retry-ms 30000
                     ::wa/timeout 300000
                     }
                    (go-promise
                     (info "get all data...")
                     (<! (api/get-all-funds)))))

(defn- get-fund-info-with-retry
  [code]
  (wa/pulling-retry {::wa/done? (comp not :failure)
                     ::wa/retry-ms 30000
                     ::wa/timeout 300000
                     }
                    (go-promise
                     (info "get fund info:" code)
                     (<! (api/get-fund-info code)))))

(defn get-all
  []
  (cache/async-lookup-or-miss C :all get-all-data-with-retry))

(defn get-fund
  [code]
  (cache/async-lookup-or-miss C code get-fund-info-with-retry))
