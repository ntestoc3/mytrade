(ns mytrade.event
  (:require [kee-frame.core :as k :refer [reg-controller reg-chain reg-event-db reg-event-fx]]
            [re-frame.core :as rf]
            [mytrade.utils :as utils :refer [>evt <sub]]))

;; 要显示的基金代码
(utils/sub-and-evt-db2 :codes [:codes])

;; 显示的基金数据
(utils/sub-and-evt-db2 :datas [:datas])

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

