(ns mytrade.subs
  (:require [kee-frame.core :as k :refer [reg-controller reg-chain reg-event-db reg-event-fx]]
            [re-frame.core :as rf]
            [cljs-time.format :as timef]
            [mytrade.utils :as utils :refer [>evt <sub]]))

(rf/reg-sub
 :unloaded-codes
 (fn [db _]
   (let [batch-size (get-in db [:load-state :batch-size])
         load-count (get-in db [:load-state :count])]
     (->> (:codes db)
          (drop load-count)
          (take batch-size)))))



(rf/reg-sub
 :date-before-str
 (fn [db _]
   (->> (:date-before db)
        (timef/unparse {:format-str "yyyy-MM-dd"}))))
