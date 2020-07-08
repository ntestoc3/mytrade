(ns mytrade.db
  (:require ["localforage" :as lf]
            [datascript.core :as d]
            [clojure.edn :as edn]
            [cljs.core.async :as async :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [oops.core :refer [gget gset! gcall oget oset! ocall oapply ocall! oapply!
                               gget+ gset!+ oget+ oset!+ ocall+ oapply+ ocall!+ oapply!+]]
            [mount.core :refer [defstate]]))


(defn load-db
  [s]
  (print "load-db:" s)
  (->> s
       (edn/read-string {:eof nil})
       (d/conn-from-db)))

(defn- start-db
  []
  (-> (ocall! lf "getItem" "mydb")
      (.then (fn [mydb]
               (if mydb
                 (load-db mydb)
                 (d/create-conn))))))

(defstate  ^{:on-reload :noop}
  conn
  :start (start-db))

(defn save-db!
  "[TODO] on mount state :stop, will run start-db first!"
  []
  (go (let [c (<p! @conn)]
        (prn "save conn:" c)
        (->> (d/db c)
             pr-str
             (ocall lf "setItem" "mydb" #(prn "save db ok!")))
        (js/alert "ok!"))))

(.addEventListener js/window "beforeunload" save-db!)

(defn transact!
  [opt]
  (go (-> (<p! @conn)
          (d/transact! opt))))

(defn reset-db!
  []
  (go (-> (<p! @conn)
          (d/reset-conn! (d/empty-db)))))

(defn get-data
  ([id]
   (go (-> (<p! @conn)
           (d/db)
           (d/pull [:*] id))))
  ([k v]
   (go (let [db (-> (<p! @conn)
                    (d/db))]
         (->> (d/q '[:find ?e
                     :in $ ?k ?v
                     :where [?e ?k ?v]]
                   db
                   k
                   v)
              (map #(->> %
                         first
                         (d/pull db [:*]))))))))

(defn range-data
  [datas start count]
  (->> datas
       (drop start)
       (take count)))

(defn update-all!
  "查找所有by-k等与by-v的数据，并修改update-k的值为(update-f (update-v))的值"
  [by-k by-v update-k update-f]
  (go (let [db (-> (<p! @conn)
                   (d/db))
            ids (d/q '[:find ?e ?uv
                       :in $ ?v ?sk ?uk
                       :where
                       [?e ?sk ?v]
                       [?e ?uk ?uv]]
                     db
                     by-v
                     by-k
                     update-k)]
        (->> ids
             (map (fn [[id uv]]
                    [:db/add id update-k (update-f uv)]))
             doall
             (transact!)))))


(comment

  (transact! [{:aa 1 :bb 2} {:aa 3 :cc 55}])

  (transact! [{:aa 33 :ee 333} {:aa 3 :gogo "hell"}])

  (go (print (<! (get-data :aa 3))))

  )
