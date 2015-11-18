(ns kilgore.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [kilgore.store :as store]))

(deftest basic
  (doseq [opts [{:type :atom, :store-atom (atom nil)}
                {:type :carmine}]]
    (testing (:type opts)
      (let [store (store/acquire opts)]
        (testing "version, events and record-event!"
          (store/delete! store "test")

          (is (= 0 (store/version store "test")))
          (store/record-event! store "test" {:test "first"})
          (store/record-event! store "test" {:test "second"})
          (is (= 2 (store/version store "test")))
          (is (= "first" (:test (first (store/events store "test")))))
          (is (= "second" (:test (last (store/events store "test"))))))

        (testing "stream-ids and delete!"
          (store/delete! store "test")
          (store/record-event! store "test" {:test "first"})

          (is (contains? (store/stream-ids store) "test"))
          (store/delete! store "test")
          (is (not (contains? (store/stream-ids store) "test"))))))))
