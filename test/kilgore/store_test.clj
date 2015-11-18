(ns kilgore.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [kilgore.store :as store]))

(deftest basic
  (doseq [opts [{:type :atom, :store-atom (atom nil)}
                {:type :carmine}]]
    (testing (:type opts)
      (let [store (store/acquire opts)
            cleanup (fn [& ids] (doseq [id ids] (store/delete! store id)))]
        (testing "record-event!"
          (cleanup "test")

          (is (= 0 (store/version store "test")))
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

          (is (= 0 (store/version store "test:1")))
          (is (= 1 (store/version store "test:2"))))))))
