(ns kilgore.store
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [taoensso.carmine :as car :refer [wcar]])
  (:import java.util.Date))

(defn- tstamp [event]
  (if (:tstamp event)
    event
    (assoc event :tstamp (Date.))))

(defprotocol IStore
  (->stream-key [_ stream-id])
  (->stream-id [_ stream-key])
  (events [_ stream-id] [_ stream-id opts])
  (record-event! [_ stream-id event])
  (version [_ stream-id])
  (stream-ids [_])
  (delete! [_ stream-id])
  (rename! [_ stream-id new-stream-id]))

(defrecord AtomStore [opts]
  IStore

  (->stream-key [_ stream-id]
    stream-id)

  (->stream-id [_ stream-id]
    stream-id)

  (events [_ stream-id] (events _ stream-id {}))

  (events [this stream-id {:keys [offset] :or {offset 0}}]
    (drop offset (get @(:store-atom opts) (->stream-key this stream-id))))

  (record-event! [this stream-id event]
    (let [event (tstamp event)]
      (swap! (:store-atom opts)
             update-in [(->stream-key this stream-id)] #((fnil conj []) % event))))

  (version [this stream-id]
    (count (get @(:store-atom opts) (->stream-key this stream-id))))

  (stream-ids [this]
    (->> @(:store-atom opts)
         keys
         (map (partial ->stream-id this))
         set))

  (delete! [this stream-id]
    (swap! (:store-atom opts)
           dissoc (->stream-key this stream-id)))

  (rename! [this stream-id new-stream-id]
    (swap! (:store-atom opts)
           (fn [v]
             (-> v
                 (assoc (->stream-key this new-stream-id)
                        (get v (->stream-key this stream-id)))
                 (dissoc (->stream-key this stream-id)))))))

(defn ->safe-filename [v]
  (s/replace v #"(?i)[^a-z0-9]" (fn [x] (str "_" (int (.charAt x 0)) "_"))))

(defn <-safe-filename [v]
  (s/replace v #"_(\d+)_" (fn [[_ x]] (str (char (Long/parseLong x))))))

(defrecord FileStore [opts]
  IStore

  (->stream-key [_ stream-id]
    (.mkdirs (io/file (:store-dir opts)))
    (io/file (:store-dir opts) (->safe-filename stream-id)))

  (->stream-id [_ stream-id] stream-id)

  (events [_ stream-id] (events _ stream-id {}))

  (events [this stream-id {:keys [offset] :or {offset 0}}]
    (let [f (->stream-key this stream-id)]
      (when (.exists f)
        (drop offset (->> f (io/reader) line-seq (map read-string))))))

  (record-event! [this stream-id event]
    (let [event (tstamp event)]
      (binding [*out* (io/writer (->stream-key this stream-id) :append true)]
        (prn event))))

  (version [this stream-id]
    (let [f (->stream-key this stream-id)]
      (if (.exists f)
        (count (line-seq (io/reader f)))
        0)))

  (stream-ids [_]
    (let [dir (io/file (:store-dir opts))]
      (set (map #(<-safe-filename (.getName %)) (.listFiles dir)))))

  (delete! [this stream-id]
    (.delete (->stream-key this stream-id)))

  (rename! [this stream-id new-stream-id]
    (.renameTo (->stream-key this stream-id)
               (->stream-key this new-stream-id))))

(defrecord CarmineStore [opts]
  IStore

  (->stream-key [_ stream-id]
    (let [{:keys [key-prefix]} opts]
      (str (when key-prefix (str key-prefix ":"))
           "events:" stream-id)))

  (->stream-id [this stream-key]
    (subs stream-key (count (->stream-key this ""))))

  (events [_ stream-id]
    (events _ stream-id {}))

  (events [this stream-id {:keys [chunk-size offset]
                           :or {chunk-size 100, offset 0}}]
    (let [chunk (wcar (:connection opts)
                      (car/lrange (->stream-key this stream-id)
                                  offset
                                  (dec (+ offset chunk-size))))]
      (if (= chunk-size (count chunk))
        (lazy-cat chunk
                  (events this stream-id {:chunk-size chunk-size
                                          :offset (+ offset chunk-size)}))
        chunk)))

  (record-event! [this stream-id event]
    (let [event (tstamp event)]
      (wcar (:connection opts)
            (car/rpush (->stream-key this stream-id) event))))

  (version [this stream-id]
    (wcar (:connection opts)
          (car/llen (->stream-key this stream-id))))

  (stream-ids [this]
    (->> (wcar (:connection opts)
               (car/keys (->stream-key this "*")))
         (map (partial ->stream-id this))
         set))

  (delete! [this stream-id]
    (wcar (:connection opts)
          (car/del (->stream-key this stream-id))))

  (rename! [this stream-id new-stream-id]
    (wcar (:connection opts)
          (car/rename (->stream-key this stream-id)
                      (->stream-key this new-stream-id)))))

(defn acquire [{:keys [type] :or {type :atom}
                :as opts}]
  {:pre [(case type
           :atom (contains? opts :store-atom)
           :file (contains? opts :store-dir)
           true)]}
  (case type
    :file (->FileStore opts)
    :atom (->AtomStore opts)
    :carmine (->CarmineStore opts)))
