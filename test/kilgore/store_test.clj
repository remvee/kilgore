(ns kilgore.store-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [kilgore.store :as store]
            [taoensso.carmine :as car])
  (:import java.nio.file.attribute.FileAttribute
           java.nio.file.Files))

(def ^:dynamic *temp-dir* nil)

(defn- rmdir-rf [dir]
  (doseq [f (-> dir io/file file-seq reverse)]
    (.delete f)))

(use-fixtures :each
  (fn with-temp-dir [f]
    (binding [*temp-dir* (str (Files/createTempDirectory "kilgore-test" (make-array FileAttribute 0)))]
      (f)
      (rmdir-rf *temp-dir*))))

(defn store-opts []
  (let [opts [{:type :atom, :store-atom (atom nil)}
              {:type :file, :store-dir *temp-dir*}]]
    (try
      (car/wcar {} (car/keys "test"))
      (conj opts {:type :carmine})
      (catch Exception _
        (prn "WARNING! Skipping carmine tests!")
        opts))))

(deftest basic
  (doseq [opts (store-opts)]
    (testing (:type opts)
      (let [store   (store/acquire opts)
            cleanup (fn [& ids] (doseq [id ids] (store/delete! store id)))]
        (testing "record-event!"
          (cleanup "test")

          (is (zero? (store/version store "test")))
          (store/record-event! store "test" {:test "first"})
          (store/record-event! store "test" {:test "second"})
          (is (= 2 (store/version store "test")))
          (is (= "first" (:test (first (store/events store "test")))))
          (is (= "second" (:test (last (store/events store "test"))))))

        (testing "delete!"
          (cleanup "test")
          (store/record-event! store "test" {:test "first"})

          (is (contains? (store/stream-ids store) "test"))
          (store/delete! store "test")
          (is (not (contains? (store/stream-ids store) "test"))))

        (testing "rename!"
          (cleanup "test:1" "test:2")

          (store/record-event! store "test:1" {:test "first"})
          (store/rename! store "test:1" "test:2")

          (is (zero? (store/version store "test:1")))
          (is (= 1 (store/version store "test:2"))))))))
