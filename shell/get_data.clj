#!/usr/bin/env bb

(ns get-data
  (:require [babashka.curl :as curl]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.edn :as edn]))

(def funds (-> (curl/get "http://fund.eastmoney.com/js/fundcode_search.js")
               :body
               (as-> $
                   (->> (str/index-of $ \=)
                        inc
                        (subs $)))
               (edn/read-string)
               (->> (map (fn [[code short-name name type full-name]]
                           {:id (Integer/parseInt code)
                            :code code
                            :short-name short-name
                            :name name
                            :type type
                            :full-name full-name})))))

(defn curr-times
  []
  (let [tz (java.time.ZoneId/of "Asia/Shanghai")
        local-now (-> (java.time.ZonedDateTime/now)
                      (.withZoneSameInstant tz))
        pattern (java.time.format.DateTimeFormatter/ofPattern "yyyyMMddHHmmss")]
    (.format local-now pattern)))

(defn get-info
  [code]
  (let [url (format "http://fund.eastmoney.com/pingzhongdata/%s.js?v=%s"
                    code
                    (curr-times))
        resp (curl/get url {:throw false})]
    (when (= 200 (:status resp))
      (->> (:body resp)
           (re-seq #"var (\w+)\s*=\s*([\[\"].*?[\]\"]);")
           (map (fn [[_ k v]]
                  [(keyword k)
                   (json/parse-string v keyword)]))
           (into {})))))


(defn get-fund-data
  [code]
  (let [url (format "http://fund.eastmoney.com/%s.html" code)
        resp (curl/get url {:throw false})]
    (when (= 200 (:status resp))
      (:body resp))
    ;; 需要解析html,bb库有点麻烦
    ))

(-> (clojure.java.io/file "datas")
    (.mkdir))

(->> funds
     (filter #(#{"股票型" "混合型" } (:type %)))
     (pmap (fn [{:keys [code] :as fund} ]
             (prn "query code:" code)
             (let [info (get-info code)]
               (when (and info
                          (not-empty {:fund_minsg info}))
                 (->> (select-keys info [:Data_ACWorthTrend :Data_grandTotal])
                      (json/encode)
                      (spit (str "datas/" code ".json")))
                 fund))))

     ;; save fund info
     (filter identity)
     (json/encode)
     (spit (str "datas/all_funds.json")))

