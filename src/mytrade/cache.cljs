(ns mytrade.cache
  (:require ["localforage" :as lf]
            [clojure.edn :as edn]
            [cljs.core.async :as async :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [oops.core :refer [gget gset! gcall oget oset! ocall oapply ocall! oapply!
                               gget+ gset!+ oget+ oset!+ ocall+ oapply+ ocall!+ oapply!+]]
            [cljs.cache :as cache :refer [CacheProtocol]])
  (:require-macros [cljs.cache :refer [defcache]]))

(extend-type js/Storage
  ICounted
  (-count [this] (.-length this))

  ISeqable
  (-seq [this]
    (->> (count this)
         range
         (map #(when-let [k (.key this %1)]
                 [k (.getItem this k)])))))

(defcache StorageCache [cache]
  CacheProtocol
  (lookup [_ item]
          (-> (.getItem cache item)
              edn/read-string))
  (lookup [_ item not-found]
          (if-some [r (.getItem cache item)]
            (edn/read-string r)
            not-found))
  (has? [_ item]
        (-> (.getItem cache item)
            nil?
            not))
  (hit [this item] this)
  (miss [this item result]
        (.setItem cache item result)
        this)
  (evict [this key]
         (.removeItem cache key))
  (seed [this base]
        this)

  Object
  (toString [_]
            (->> (.-length cache)
                 range
                 (map #(.key cache %1)))))

(def ls-cache (StorageCache. js/localStorage))

(comment

  (def C (StorageCache. js/localStorage))

  (cache/has? C :a)

  (cache/evict C :a)

  (cache/miss C :a 1234 )

  (cache/lookup C :a)

  (cache/miss C :b {:a 1 :b 2 :c [1 2]})

  (cache/lookup C :b)


  )
