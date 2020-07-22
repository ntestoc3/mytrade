(ns mytrade.event
  (:require [kee-frame.core :as k :refer [reg-controller reg-chain reg-event-db reg-event-fx]]
            [re-frame.core :as rf]
            [cljs-time.core :as time]
            [cljs-time.format :as timef]
            [cljs.core.async :as async :refer [go <!]]
            [mytrade.data :as data]
            [mytrade.utils :as utils :refer [>evt <sub >evt-sync]]
            [taoensso.timbre :as timbre
             :refer-macros [log  trace  debug  info  warn  error  fatal  report
                            logf tracef debugf infof warnf errorf fatalf reportf
                            spy get-env]]))

;; 要显示的基金代码
(utils/sub-and-evt-db2 :codes [:codes])

;; 显示的基金数据
(utils/sub-and-evt-db2 :datas [:datas])

(utils/sub-and-evt-db2 :date-before [:date-before])

;;每次刷新数据大小
(utils/sub-and-evt-db2 :batch-size [:load-state :batch-size])

;; 是否还有数据要显示
(utils/sub-and-evt-db2 :have-data? [:load-state :have-data?])

;; 是否处于加载状态
(utils/sub-and-evt-db2 :loading? [:load-state :loading?])

;; 已经加载数据的基金数量
(utils/sub-and-evt-db2 :load-count [:load-state :count])

(rf/reg-event-db
 :add-datas
 (fn [db [_ data]]
   (update-in db [:datas] conj data)))

(rf/reg-event-db
 :datas-take-over
 (fn [db _]
   (let [batch-size (get-in db [:load-state :batch-size])
         new-count (+ (get-in db [:load-state :count])
                      batch-size)
         codes-count (count (:codes db))]
     (cond-> db
       (>= new-count codes-count) (assoc-in [:load-state :have-data?] false)
       :always (assoc-in [:load-state :count] new-count)))))

;; ------------------------
;; Data helper

(defn time-before?
  [ts]
  (-> (timef/parse {:format-str "yyyy-MM-dd"} ts)
      (time/before? (<sub [:date-before]))))

(defn take-codes
  []
  (go
    (try
      (info "take-codes.")
      (when-not (seq (<sub [:codes]))
        (->> (data/get-all)
             <!
             (filter #(not (identical? (:start-date %1) "--")))
             (filter (comp time-before? :start-date))
             (sort-by :slope)
             reverse
             (map :code)
             (>evt-sync [:codes]))
        true)
      (catch :default e
        (error "take-codes:" e)))))

(defn take-datas []
  (when-not (<sub [:loading?])
    (info "take-datas!!")
    (go
      (try
        (>evt [:loading? true])
        (<! (take-codes))
        (info "take codes ok.")
        (doseq [code (<sub [:unloaded-codes])]
          (->> (data/get-fund code)
               <!
               (>evt [:add-datas]))
          ;; 通过timeout交出cpu执行，让ui正常刷新
          (<! (async/timeout 200)))
        (>evt [:datas-take-over])
        (catch :default e
          (error "take data:" e))
        (finally
          (>evt [:loading? false]))))))

(reg-event-fx :take-datas
              (fn [_ _]
                (take-datas)
                nil))
