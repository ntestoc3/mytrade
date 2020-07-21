(ns mytrade.core
  (:require
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [re-frame.core :as rf]
   [cljs.core.async :as async :refer [go <!]]
   [com.wsscode.async.async-cljs :as wa :refer [go-promise <? <?maybe]]
   [cljs-time.core :as time]
   [cljs-time.format :as timef]
   [cljs-time.coerce :as timec]
   [reitit.frontend :as reitit]
   [kee-frame.core :as k :refer [reg-controller reg-chain reg-event-db reg-event-fx]]
   [kee-frame.error :as error]
   [kee-frame.scroll]
   [mytrade.charts :refer [stock]]
   [mytrade.cache]
   [mytrade.api]
   [mytrade.subs]
   [mytrade.event]
   [mytrade.data :as data]
   [mytrade.utils :refer [>evt <sub >evt-sync]]
   [mytrade.infinite-scroll :refer [infinite-scroll]]
   [oops.core :refer [gget gset! gcall oget oset! ocall oapply ocall! oapply!
                      gget+ gset!+ oget+ oset!+ ocall+ oapply+ ocall!+ oapply!+]]
   [taoensso.timbre :as timbre
    :refer-macros [log  trace  debug  info  warn  error  fatal  report
                   logf tracef debugf infof warnf errorf fatalf reportf
                   spy get-env]]
   [clojure.string :as str]))

;; ------------------------
;; Data helper
(defn take-codes
  []
  (go
    (try
      (info "take-codes.")
      (when-not (seq (<sub [:codes]))
        (->> (data/get-all)
             <!
             (map :code)
             (>evt-sync [:codes])))
      (catch :default e
        (error "take-codes:" e)))))

(defn take-slope-codes
  []
  (go
    (try
      (info "take slope codes.")
      (when-not (seq (<sub [:codes]))
        (->> (data/get-slopes)
             <!
             (map :code)
             (>evt-sync [:codes])))
      (catch :default e
        (error "take slope codes:" e)))))

(defn take-datas []
  (when-not (<sub [:loading?])
    (info "take-datas!!")
    (go
      (try
        (>evt [:loading? true])
        ;;(<! (take-codes))
        (<! (take-slope-codes))
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


;; -------------------------
;; Page components

(defn format-unix-time
  [ut]
  (->> (timec/from-long ut)
       (timef/unparse {:format-str "yyyy-MM-dd"})))

(defn format-sy-point
  [p]
  (str
   "<span style=\"color:" (oget p "series.color") "\">"
   (oget p "series.name")
   "</span>:<b>"
   (-> (oget p "y")
       (.toFixed 2))
   "%</b>"))

(defn pingan-chart []
  (fn [{:keys [Data_ACWorthTrend
               Data_grandTotal
               managers
               code
               type
               name]}]
    (info "stock:" code type)
    [:div.card
     [:header.card-header>p.card-header-title
      (str "代码: " code " -- " name " ------ " type)]
     [:div.card-content>div.content>div.columns
      [:div.column
       [stock
        {:chart-data {:rangeSelector {:selected 5},
                      :title {:text (str name "  累计净值走势")},
                      :plotOptions {:series {:showInLegend true}},
                      :tooltip {:split false
                                :shared false
                                :valeDecimals 4
                                :style {:fontSize "16px"}
                                :formatter #(this-as tooltip
                                              (str (-> (oget tooltip "x")
                                                       format-unix-time)
                                                   "<br/>"
                                                   (when-let [y (oget tooltip "y")]
                                                     (str " 累计净值: "
                                                          (.toFixed y 4)))
                                                   (when-let [text (oget tooltip "?point.?text")]
                                                     text)
                                                   "<br/>"))
                                },
                      :series [{:id "datas"
                                :type "area"
                                :name name
                                :data Data_ACWorthTrend
                                }
                               {:id "经理人信息"
                                :type "flags"
                                :color "#5F86B3"
                                :fillColor "#5F86B3"
                                ;; :onSeries "datas"
                                :shape "circlepin"
                                :dataLabels {:shape "circle"}
                                :width 20
                                :textAlign "center"
                                :style {:color "white"}
                                :states {:hover {:fillColor "#395C84"}}
                                :data managers
                                }]}}]]
      [:div.column
       [stock
        {:chart-data {:rangeSelector {:selected 5},
                      :title {:text (str name "  累计收益率走势")},
                      :yAxis {:labels {:formatter #(this-as axis
                                                     (str (when (pos? (oget axis "value"))
                                                            " + " )
                                                          (-> (oget axis "value")
                                                              (.toFixed 2))
                                                          "%"))}}
                      :tooltip {:valeDecimals 2
                                :xDateFormat "%Y-%m-%d"
                                :style {:fontSize "16px"}
                                :formatter #(this-as tooltip
                                              (str (-> (.-x tooltip) format-unix-time)
                                                   "<br/>"
                                                   (if-let [text (oget tooltip "?point.?text")]
                                                     text
                                                     (->> (.-points tooltip)
                                                          (map format-sy-point)
                                                          (str/join "<br/>")))))
                                },
                      :series (conj Data_grandTotal
                                    {:id "经理人信息"
                                     :type "flags"
                                     :color "#5F86B3"
                                     :fillColor "#5F86B3"
                                     ;; :onSeries "datas"
                                     :shape "circlepin"
                                     :dataLabels {:shape "circle"}
                                     :width 20
                                     :textAlign "center"
                                     :style {:color "white"}
                                     :states {:hover {:fillColor "#395C84"}}
                                     :data managers
                                     }
                                    )}}]]]]))
(defn home-page []
  (fn []
    [:div.columns.is-centered
     [:div.column.is-four-fifths
      (for [d (<sub [:datas])]
        ^{:key (:code d)}
        [:div
         [pingan-chart d]
         [:br]])

      ;; 滚动加载
      [infinite-scroll
       {:can-show-more? (<sub [:have-data?])
        :load-fn take-datas}]
      [:button.button.is-fullwidth
       {:class (when (<sub [:loading?])
                 "is-loading")
        :on-click #(take-datas)}
       "加载更多"]
      ]]))


(defn about-page []
  (fn [] [:span.main
          [:h1 "About vtrade"]]))

;; -------------------------
;; Page mounting component

(defn current-page [main]
  (fn []
    [:div
     [:header
      [:p [:a {:href (k/path-for [:index])} "Home"] " | "
       [:a {:href (k/path-for [:about])} "About vtrade"]]]
     main
     [:footer
      [:p "vtrade was generated by the "
       [:a {:href "https://github.com/reagent-project/reagent-template"} "Reagent Template"] "."]]]))

;; -------------------------
;; Routes

(def routes
  [["/" :index]
   ["/about" :about]])

;; dispatch
(defn error-body [[err info]]
  (js/console.log "An error occurred: " info)
  (js/console.log "Context: " err)
  [:div "Something went wrong"])

(defn dispatch-main []
  [error/boundary
   error-body
   [k/switch-route (comp :name :data)
    :index [home-page]
    :about [about-page]
    nil [:div "Loading..."]]])

;; -------------------------
;; Initialize app

(defn init []
  (k/start! {:debug? true
             :routes routes
             :initial-db {:codes []
                          :datas []
                          :load-state {:have-data? true
                                       :loading? false
                                       :count 0
                                       :batch-size 8
                                       }}
             :not-found "/"
             :root-component [current-page [dispatch-main]]
             }))
