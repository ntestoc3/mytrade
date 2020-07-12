
(require [hy.extra.anaphoric [*]])
(import [bs4 [BeautifulSoup]])
(import requests)
(import json)
(import re)
(import os)
;; 对于I/O操作,使用线程池可以降低cpu和内存占用
;; 去掉.dummy就使用多进程
(import [multiprocessing.dummy [Pool]])
(import logging)
(import [datetime [datetime]])

(defmacro ->2> [head &rest args]
  "Thread macro for second arg"
  (setv ret head)
  (for [node args]
    (setv ret (if (isinstance node HyExpression)
                  `(~(first node) ~(second node) ~ret ~@(drop 2 node))
                  `(~node ~ret))))
  ret)

(defn select-keys
  [d ks]
  (->> (.items d)
       (filter #%(-> (first %1)
                     (in ks)))
       dict))

(defn pmap
  [f datas &optional [proc 5]]
  ":proc 为进程数量"
  (with [pool (Pool :processes proc)]
    (pool.map f datas)))

(defn curr-times
  []
  "当前时间字符串"
  (-> (.now datetime)
      (.strftime "%Y%m%d%H%M%S")))

(defn get-all-funds
  []
  "获取所有基金条目"
  (-> (requests.get "http://fund.eastmoney.com/js/fundcode_search.js")
      (doto (setattr "encoding" "gbk2312"))
      (. text)
      (->> (re.search "(\[\[.*\]\])"))
      (.group 0)
      (.replace "," " ")
      read-str
      eval
      (->> (map #%(->> %1
                       (zip ["code" "short-name" "name" "type" "full-name"])
                       dict)))))

(defn get-fund-history-info
  [code]
  "获取fund历史信息"
  (setv t (curr-times))
  (setv data (requests.get f"http://fund.eastmoney.com/pingzhongdata/{code}.js?v={t}"))
  (when (= 200 data.status-code)
    (setattr data "encoding" "gbk2312")
    (->2> data.text
          (re.findall r"var (\w+)\s*=\s*([\[\"].*?[\]\"])\s*;")
          (lfor [k v] [k
                       (-> (.replace v "'" "\"")
                           (json.loads))])
          dict)))

(defn select-table-text
  [table]
  (lfor r (.select table "tr")
        (lfor c (.select r "td")
              (.get-text c " " :strip True))))

(defn parse-manager-info
  [data]
  "解析基金经理变动信息"
  (-> (.select-one data "li#fundManagerTab table")
      (select-table-text)
      rest
      (->2> (lfor row
                  (-> (zip ["work-range" "name" "days" "percent"] row)
                      (dict))))))

(defn get-manager-info
  [code]
  (-> (requests.get f"http://fund.eastmoney.com/{code}.html")
      (doto (setattr "encoding" "gbk2312"))
      (. text)
      (BeautifulSoup "lxml")
      parse-manager-info))

(defn save-data
  [fname data]
  (with [f (-> (os.path.join data-dir fname)
               (open "w"))]
    (json.dump data f :ensure-ascii False)))

(defn save-fund-info
  [fund]
  (setv code (of fund "code"))
  (logging.info "save-fund-info: %s" code)
  (setv info (get-fund-history-info code))
  (if (and info
             (.get info "fund_minsg"))
      (do
        (setv info (select-keys info ["Data_ACWorthTrend" "Data_grandTotal"]))
        (->> (get-manager-info code)
             (assoc info "managers"))
        (save-data f"{code}.json" info)
        (logging.info "save-fund-info: %s, ok!" code)
        fund)
      (logging.info "save-fund-info: %s, skipped!" code)))

(setv data-dir "datas/")

(defn save-all-info
  []
  (os.makedirs data-dir :exist-ok True)
  (->2> (get-all-funds)
        (filter #%(-> (of %1 "type")
                      (in #{"股票型" "混合型"})))
        (pmap save-fund-info :proc 8)
        (filter identity)
        list
        (save-data "all_funds.json")))

(defmain [&rest args]
  (logging.basicConfig :level logging.INFO
                       :filename "app.log"
                       :filemode "w"
                       :style "{"
                       :format "{asctime} [{levelname}] {filename}({funcName})[{lineno}] {message}")
  (save-all-info)
  (logging.info "over!"))
