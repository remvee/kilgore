(ns kilgore.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [kilgore.store :as store]))

(deftest atom-store
  (testing "record-event!"
    (let [store-atom (atom nil)
          store (store/acquire {:store-atom store-atom})]
      (is (= 0 (store/version store "test")))

      (store/record-event! store "test" {:test "first"})
      (store/record-event! store "test" {:test "second"})
      (is (= 2 (store/version store "test")))
      (is (= "first" (:test (first (store/events store "test")))))
      (is (= "second" (:test (last (store/events store "test"))))))))

(deftest carmine-store
  (testing "record-event!"
    (let [store (store/acquire {:type :carmine})]
      (store/delete! store "test")
      (is (= 0 (store/version store "test")))

      (store/record-event! store "test" {:test "first"})
      (store/record-event! store "test" {:test "second"})
      (is (= 2 (store/version store "test")))
      (is (= "first" (:test (first (store/events store "test")))))
      (is (= "second" (:test (last (store/events store "test"))))))))
