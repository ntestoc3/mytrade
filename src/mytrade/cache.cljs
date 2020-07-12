(ns mytrade.cache
  (:require [clojure.edn :as edn]
            [com.wsscode.async.async-cljs :as wa :refer [go-promise <? <?maybe]]
            [cljs.cache :as c]))

(extend-type js/Storage
  ICounted
  (-count [this] (.-length this))

  ILookup
  (-lookup
    ([this k]
     (some-> (.getItem this k)
             (edn/read-string)))
    ([this k not-found]
     (if-some [r (.getItem this k)]
       (edn/read-string r)
       not-found)))

  IAssociative
  (-contains-key? [this k]
    (-> (.getItem this k)
        nil?
        not))
  (-assoc [this k v]
    (.setItem this k (pr-str v))
    this)

  IMap
  (-dissoc [this k]
    (.removeItem this k)
    this)

  IMapEntry
  (-key [coll]
    (first coll))
  (-val [coll]
    (second coll))

  ISeqable
  (-seq [this]
    (->> (count this)
         range
         (map #(when-let [k (.key this %1)]
                 [k (get this k)])))))

;;; cache healper

(defn lookup
  "Retrieve the value associated with `e` if it exists, else `nil` in
  the 2-arg case.  Retrieve the value associated with `e` if it exists,
  else `not-found` in the 3-arg case.
  Reads from the current version of the atom."
  ([cache-atom e]
   (c/lookup @cache-atom e))
  ([cache-atom e not-found]
   (c/lookup @cache-atom e not-found)))

(def ^{:private true} default-wrapper-fn #(%1 %2))

(defn through-cache-old
  "The basic hit/miss logic for the cache system.  Like through but always has
  the cache argument in the first position for easier use with swap! etc."
  ([cache item] (through-cache-old cache item default-wrapper-fn identity))
  ([cache item value-fn] (through-cache-old cache item default-wrapper-fn value-fn))
  ([cache item wrap-fn value-fn]
   (if (c/has? cache item)
     (c/hit cache item)
     (c/miss cache item (wrap-fn #(value-fn %) item)))))

(defn lookup-or-miss
  "Retrieve the value associated with `e` if it exists, else compute the
  value (using value-fn, and optionally wrap-fn), update the cache for `e`
  and then perform the lookup again.
  value-fn (and wrap-fn) will only be called (at most) once even in the
  case of retries, so there is no risk of cache stampede.
  Since lookup can cause invalidation in some caches (such as TTL), we
  trap that case and retry (a maximum of ten times)."
  ([cache-atom e value-fn]
   (lookup-or-miss cache-atom e default-wrapper-fn value-fn))
  ([cache-atom e wrap-fn value-fn]
   (let [d-new-value (delay (wrap-fn value-fn e))]
     (loop [n 0
            v (c/lookup (swap! cache-atom
                               through-cache-old
                               e
                               default-wrapper-fn
                               (fn [_] @d-new-value))
                        e
                        ::expired)]
       (when (< n 10)
         (if (= ::expired v)
           (recur (inc n)
                  (c/lookup (swap! cache-atom
                                   through-cache-old
                                   e
                                   default-wrapper-fn
                                   (fn [_] @d-new-value))
                            e
                            ::expired))
           v))))))

(defn async-lookup-or-miss
  "Retrieve the value associated with `e` if it exists, else compute the
  value (using value-fn, and optionally wrap-fn), update the cache for `e`
  and then perform the lookup again. "
  ([cache-atom e value-fn]
   (async-lookup-or-miss cache-atom e default-wrapper-fn value-fn))
  ;; 使用delay函数的话，会被async污染，导致cache-atom变为promise
  ([cache-atom e wrap-fn value-fn]
   (go-promise
    (if (c/has? @cache-atom e)
      (get @cache-atom e)
      (let [d-new-value (<?maybe (wrap-fn value-fn e))]
        (swap! cache-atom
               through-cache-old
               e
               default-wrapper-fn
               (constantly d-new-value))
        d-new-value)))))

(defn has?
  "Checks if the cache contains a value associated with `e`.
  Reads from the current version of the atom."
  [cache-atom e]
  (c/has? @cache-atom e))

(defn hit
  "Is meant to be called if the cache is determined to contain a value
  associated with `e`.
  Returns the updated cache from the atom. Provided for completeness."
  [cache-atom e]
  (swap! cache-atom c/hit e))

(defn miss
  "Is meant to be called if the cache is determined to **not** contain a
  value associated with `e`.
  Returns the updated cache from the atom. Provided for completeness."
  [cache-atom e ret]
  (swap! cache-atom c/miss e ret))

(defn evict
  "Removes an entry from the cache.
  Returns the updated cache from the atom."
  [cache-atom e]
  (swap! cache-atom c/evict e))

(defn seed
  "Is used to signal that the cache should be created with a seed.
  The contract is that said cache should return an instance of its
  own type.
  Returns the updated cache from the atom. Provided for completeness."
  [cache-atom base]
  (swap! cache-atom c/seed base))

(defn through
  "The basic hit/miss logic for the cache system.  Expects a wrap function and
  value function.  The wrap function takes the value function and the item in question
  and is expected to run the value function with the item whenever a cache
  miss occurs.  The intent is to hide any cache-specific cells from leaking
  into the cache logic itelf."
  ([cache-atom item] (through default-wrapper-fn identity cache-atom item))
  ([value-fn cache-atom item] (through default-wrapper-fn value-fn cache-atom item))
  ([wrap-fn value-fn cache-atom item]
   (swap! cache-atom through-cache-old item wrap-fn value-fn)))

(defn through-cache
  "The basic hit/miss logic for the cache system.  Like through but always has
  the cache argument in the first position."
  ([cache-atom item] (through-cache cache-atom item default-wrapper-fn identity))
  ([cache-atom item value-fn] (through-cache cache-atom item default-wrapper-fn value-fn))
  ([cache-atom item wrap-fn value-fn]
   (swap! cache-atom through-cache-old item wrap-fn value-fn)))

(defn basic-cache-factory
  "Returns a pluggable basic cache initialied to `base`"
  [base]
  (atom (c/basic-cache-factory base)))

(defn lru-cache-factory
  "Returns an LRU cache with the cache and usage-table initialied to `base` --
   each entry is initialized with the same usage value.
   This function takes an optional `:threshold` argument that defines the maximum number
   of elements in the cache before the LRU semantics apply (default is 32)."
  [base & {threshold :threshold :or {threshold 32}}]
  (atom (c/lru-cache-factory base :threshold threshold)))

(defn ttl-cache-factory
  "Returns a TTL cache with the cache and expiration-table initialized to `base` --
   each with the same time-to-live.
   This function also allows an optional `:ttl` argument that defines the default
   time in milliseconds that entries are allowed to reside in the cache."
  [base & {ttl :ttl :or {ttl 2000}}]
  (atom (c/ttl-cache-factory base :ttl ttl)))

(defn localstore-cache
  [& {threshold :threshold
      :or {threshold 32}}]
  (lru-cache-factory js/localStorage :threshold threshold))

(comment

  (count js/localStorage )

  (get js/localStorage :a )

  (assoc js/localStorage :e "test")

  (assoc js/localStorage :a [1 2 3])

  (dissoc js/localStorage :e)

  (get js/localStorage :e)

  (def C  (localstore-cache :threshold 2))

  (through-cache C :a (constantly 1))

  (get @C :a)

  (through-cache C :b (constantly "2"))

  (get @C :b)

  (through-cache C :c (constantly [3 1]))

  (get @C :c)

  (through-cache C :e (constantly {:e 4}))

  (get @C :e)

  (hit C :b)

  (through-cache C :f (constantly {:f {:f 5}}))

  (get @C :b)

  (get @C :c)

  (doseq [i (range 25)]
    (through-cache C (str "key" i) (constantly (str "val-" i))))

  )
