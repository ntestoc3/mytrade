(ns mytrade.core
  (:require
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [reagent.session :as session]
   [cljs.core.async :as async :refer [go <!]]
   [com.wsscode.async.async-cljs :as wa :refer [go-promise <? <?maybe]]
   [cljs-time.core :as time]
   [cljs-time.format :as timef]
   [cljs-time.coerce :as timec]
   [reitit.frontend :as reitit]
   [mytrade.charts :refer [stock]]
   [mytrade.cache]
   [mytrade.api]
   [mytrade.data :as data]
   [mytrade.infinite-scroll :refer [infinite-scroll]]
   [clerk.core :as clerk]
   [accountant.core :as accountant]
   [oops.core :refer [gget gset! gcall oget oset! ocall oapply ocall! oapply!
                      gget+ gset!+ oget+ oset!+ ocall+ oapply+ ocall!+ oapply!+]]
   [taoensso.timbre :as timbre
    :refer-macros [log  trace  debug  info  warn  error  fatal  report
                   logf tracef debugf infof warnf errorf fatalf reportf
                   spy get-env]]
   [clojure.string :as str]))

;; -------------------------
;; Routes


(def router
  (reitit/router
   [["/" :index]
    ["/about" :about]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

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

(def datas (atom []))
(def have-data (atom true))
(def codes (atom []))
(def load-count (atom 0))
(def batch-size 8)

(defn take-codes
  []
  (go
    (try
      (info "take-codes.")
      (when-not (seq @codes)
        (->> (data/get-all)
             <!
             (map :code)
             (reset! codes)))
      (catch :default e
        (error "take-codes:" e)))))

(defn take-datas []
  (info "take-datas!!")
  (go
    (try
      (<! (take-codes))
      (doseq [code (->> @codes
                        (drop @load-count)
                        (take batch-size))]
        (->> (data/get-fund code)
             <!
             (swap! datas conj)))
      (swap! load-count #(+ batch-size %1))
      (when (>= @load-count (count @codes))
        (reset! have-data false))
      (catch :default e
        (error "take data:" e)))))

(defn home-page []
  (fn []
    [:div.columns.is-centered
     [:div.column.is-four-fifths
      (for [d @datas]
        ^{:key (:code d)}
        [:div
         [pingan-chart d]
         [:br]])

      ;; 滚动加载
      [infinite-scroll
       {:can-show-more? @have-data
        :load-fn take-datas}]
      ]]))


(defn about-page []
  (fn [] [:span.main
          [:h1 "About vtrade"]]))


;; -------------------------
;; Translate routes -> page components


(defn page-for [route]
  (case route
    :index #'home-page
    :about #'about-page))


;; -------------------------
;; Page mounting component


(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       [:header
        [:p [:a {:href (path-for :index)} "Home"] " | "
         [:a {:href (path-for :about)} "About vtrade"]]]
       [page]
       [:footer
        [:p "vtrade was generated by the "
         [:a {:href "https://github.com/reagent-project/reagent-template"} "Reagent Template"] "."]]])))

;; -------------------------
;; Initialize app

(defn mount-root []
  (rdom/render [current-page] (.getElementById js/document "app")))

(comment
  (go (->> (range 10)
           (map #(go %1))
           ;; 注意map返回的chan必须为closed,否则会挂起
           async/merge
           (async/reduce conj [])
           <?maybe
           (js/console.log "result:")))

  (let [c1 (async/chan)
        c2 (async/timeout 3000)]
    (go (let [[value c] (async/alts! [c1 c2])]
          (js/console.log "value:" value)
          (when (= c c2)
            (js/console.log "timouted!"))))
    c1))

(defn init []
  (take-datas)
  (clerk/initialize!)
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [match (reitit/match-by-path router path)
            current-page (:name (:data  match))
            route-params (:path-params match)]
        (reagent/after-render clerk/after-render!)
        (session/put! :route {:current-page (page-for current-page)
                              :route-params route-params})
        (clerk/navigate-page! path)))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))
