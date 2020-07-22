
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
(import [ana [calc-poly]])

;; pip install bs4 requests lxml

(defmacro ->2> [head &rest args]
  "Thread macro for second arg"
  (setv ret head)
  (for [node args]
    (setv ret (if (isinstance node HyExpression)
                  `(~(first node) ~(second node) ~ret ~@(drop 2 node))
                  `(~node ~ret))))
  ret)

(defmacro some-> [head &rest args]
  "Thread macro for first arg if not None"
  (setv g (gensym "some->"))
  (setv steps (->> args
                   (map (fn [step]
                          `(if (none? ~g)
                               None
                               (-> ~g ~step))))
                   list))
  (setv set-steps (map (fn [step]
                         `(setv ~g ~step))
                       (butlast steps)))
  `(do (setv ~g ~head)
       ~@set-steps
       ~(if (empty? (list steps))
            g
            (last steps))))


(defmacro bench [&rest body]
  (import time)
  (setv start-time (gensym "start-time"))
  (setv end-time (gensym "end-time"))
  `(do (setv ~start-time (time.time))
       ~@body
       (setv ~end-time (time.time))
       (print (.format "total run time: {:.5f} s" (- ~end-time ~start-time)))))

(defmacro with-exception [&rest body]
  `(try
     ~@body
     (except [e Exception]
       (logging.error "exception: %s" e))))

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

(defn date->ms-timestamp
  [date]
  (-> (datetime.strptime date "%Y-%m-%d")
      (.timestamp)
      (* 1000)
      int)
  )

(setv common-headers {"user-agent" "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.87 Safari/537.36"})

(defn get-all-funds
  []
  "获取所有基金条目"
  (-> (requests.get "http://fund.eastmoney.com/js/fundcode_search.js"
                    :headers common-headers)
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

(defn get-fund-total-syl
  [code]
  "获取累计收益率"
  (setv data (requests.get "http://api.fund.eastmoney.com/pinzhong/LJSYLZS"
                           :params {"fundCode" code
                                    "indexcode" "000300"
                                    "type" "se"
                                    }
                           :headers {#** common-headers
                                     "Accept" "application/json"
                                     "Referer" f"http://fund.eastmoney.com/{code}.html"}))
  (when (= 200 data.status-code)
    (setv result (data.json))
    (if (zero? (of result "ErrCode" ))
        (of result "Data")
        (logging.error (format "get-fund-total-syl {0} msg:{1}"
                               (of result "ErrCode")
                               (of result "ErrMsg"))))))

(defn get-fund-history-info
  [code]
  "获取fund历史信息"
  (setv t (curr-times))
  (setv data (requests.get f"http://fund.eastmoney.com/pingzhongdata/{code}.js?v={t}"
                           :headers (dict common-headers
                                          :referer f"http://fund.eastmoney.com/{code}.html")))
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
      (->> (map (fn [row]
                  (setv [work-range name days percent] row)
                  {"x" (-> work-range
                           (.split "~")
                           first
                           date->ms-timestamp)
                   "title" (first name)
                   "text" f"{name}<br/>任职时间[{work-range}]:共{days}, 增长率:{percent}"}))
           list)))

(defn find-contain-text
  [info ele text]
  (->> (.select info ele)
       (filter #%(in text (. %1 text)))))

(defn find-contain-text-one
  [info ele text]
  (-> (find-contain-text info ele text)
      first))

(defn get-info-value
  [info head-text]
  (-> (find-contain-text-one info "td" head-text)
      (.  text )
      (.split "：" )
      second))

(defn parse-fund-info
  [data]
  (setv infos (.select-one data "div.infoOfFund table"))
  {"start-date" (get-info-value infos "成 立 日")
   "scale" (get-info-value infos "基金规模")})

(defn get-code-page-info
  [code]
  (setv body (-> (requests.get f"http://fund.eastmoney.com/{code}.html")
                 (doto (setattr "encoding" "gbk2312"))
                 (. text)
                 (BeautifulSoup "lxml")))
  body
  {#** (parse-fund-info body)
   "managers" (parse-manager-info body)})

(defn save-data
  [fname data]
  (with [f (-> (os.path.join data-dir fname)
               (open "w"))]
    (json.dump data f :ensure-ascii False :indent 4)))

(defn save-fund-info
  [fund]
  (setv code (of fund "code"))
  (logging.info "save-fund-info: %s" code)
  (setv info (get-fund-history-info code))
  (if (and info
           (.get info "fund_minsg"))
      (do
        (setv ac-trend (of info "Data_ACWorthTrend"))
        (setv desc-info {#** fund
                         #** (get-code-page-info code)
                         "slope" (some-> (calc-poly ac-trend)
                                         first)})
        (->> {#** desc-info
              "Data_ACWorthTrend" ac-trend
              "Data_grandTotal" (get-fund-total-syl code)}
             (save-data f"{code}.json"))
        desc-info)
      (logging.info "save-fund-info: %s, skipped!" code)))

(setv data-dir "datas/")

(defn save-all-info
  []
  (os.makedirs data-dir :exist-ok True)
  (->2> (get-all-funds)
        (filter #%(-> (of %1 "type")
                      (in #{"股票型" "混合型"})))
        (pmap #%(with-exception (save-fund-info %1)) :proc 8) ;; 超过15分钟就会被断开连接
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
