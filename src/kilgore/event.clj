(ns kilgore.event
  (:require [clojure.string :refer [capitalize join split]]))

(defprotocol IEvent)

(defmacro defevent [type fields]
  (let [type-class-name (join "" (map capitalize (split (str type) #"-")))]
    `(do
       (defrecord ~(symbol type-class-name) ~fields IEvent)
       (def ~type ~(symbol type-class-name)))))
