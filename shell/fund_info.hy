
(require [hy.extra.anaphoric [*]])
(import [bs4 [BeautifulSoup]])
(import requests)
(import json)
(import re)
(import [datetime [datetime]])
(import os)
;; 对于I/O操作,使用线程池可以降低cpu和内存占用
;; 去掉.dummy就使用多进程
(import [multiprocessing.dummy [Pool]])
(import logging)

(defmacro ->2> [head &rest args]
  "Thread macro for second arg"
  (setv ret head)
  (for [node args]
    (setv ret (if (isinstance node HyExpression)
                  `(~(first node) ~(second node) ~ret ~@(drop 2 node))
                  `(~node ~ret))))
  ret)

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
      eval))

(defn curr-times
  []
  "当前时间字符串"
  (-> (.now datetime)
      (.strftime "%Y%m%d%H%M%S")))

(defn get-fund-history-info
  [code]
  "获取fund历史信息"
  (setv t (curr-times))
  (setv data (requests.get f"http://fund.eastmoney.com/pingzhongdata/{code}.js?v={t}"))
  (when (= 200 data.status-code)
    (setattr data "encoding" "gbk2312")
    (->> data.text
         (re.findall r"var (\w+)\s*=\s*([\[\"].*?[\]\"])\s*;")
         (map (fn [x]
                [(first x)
                 (-> (second x)
                     (.replace "'" "\"")
                     (json.loads))]))
         list
         dict)))


(defn parse-manager-info
  [data]
  "解析基金经理变动信息"
  (-> (.find data "li" :id "fundManagerTab")
      (. table)
      (.find-all "tr")
      (->> rest
           (ap-map (->> (zip ["work-range" "name" "days" "percent"]
                             [(-> (.find it "td" :class_ "td01")
                                  (. text))
                              (-> (.find it "td" :class_ "td02")
                                  (. a text))
                              (-> (.find it "td" :class_ "td03")
                                  (. text))
                              (-> (.find it "td" :class_ "td04")
                                  (. text))
                              ])
                        (dict)))
           list)))

(defn get-manager-info
  [code]
  (-> (requests.get f"http://fund.eastmoney.com/{code}.html")
      (doto (setattr "encoding" "gbk2312"))
      (. text)
      (BeautifulSoup "lxml")
      parse-manager-info))

(defn select-keys
  [d ks]
  (->> (.items d)
       (filter #%(-> (first %1)
                     (in ks)))
       dict))

(defn save-data
  [fname data]
  (with [f (-> (os.path.join data-dir fname)
               (open "w"))]
    (json.dump data f :ensure-ascii False)))

(defn save-fund-info
  [fund]
  (setv code (of fund 0))
  (logging.info "save-fund-info: %s" code)
  (setv info (get-fund-history-info code))
  (when (and info
             (.get info "fund_minsg"))
    (setv info (select-keys info ["Data_ACWorthTrend" "Data_grandTotal"]))
    (->> (get-manager-info code)
         (assoc info "managers"))
    (save-data f"{code}.json" info)
    (logging.info "save-fund-info: %s, ok!" code)
    fund))

(setv data-dir "datas/")

(defn pmap
  [f datas &optional [proc 5]]
  ":proc 为进程数量"
  (with [pool (Pool :processes proc)]
    (pool.map f datas)))

(defn save-all-info
  []
  (os.makedirs data-dir :exist-ok True)
  (->2> (get-all-funds)
        (filter #%(-> (of %1 3)
                      (in #{"股票型" "混合型"})))
        (pmap save-fund-info :proc 8)
        (filter identity)
        list
        (save-data "all_funds.json")))

(defmain [&rest args]
  (logging.basicConfig :level logging.INFO)
  (save-all-info)
  (logging.info "over!"))
