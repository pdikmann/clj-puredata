(ns clj-puredata.translate-test
  (:require  [clj-puredata.translate :refer :all]
             [clojure.test :refer :all]))

(deftest a-test
  (testing "The translation unit"
    (testing "translates single nodes to their PD representation; with merged default-options for :x and :y etc."
      (is (= (translate-node {:type :node
                              :op "+" :id -1
                              :options {} :args [1 2 3]})
             "#X obj 0 0 + 1 2 3;"))
      (is (= (translate-node {:type :node
                              :op "+" :id 0
                              :options {} :args []})
             "#X obj 0 0 +;"))
      (is (= (translate-node {:type :node
                              :op "text" :id 0
                              :options {:x 10 :y 20} :args ["hello" "world"]})
             "#X text 10 20 hello world;")))
    (testing "does connections, too! (with TRANSLATE-LINE)"
      (is (= (translate-line {:type :connection
                              :from-node {:id 1 :outlet 2}
                              :to-node {:id 3 :inlet 4}})
             "#X connect 1 2 3 4;")))
    (testing "dispatches accordingly (with TRANSLATE-LINE)"
      (is (re-matches #"#X connect.*" (translate-line {:type :connection})))
      (is (re-matches #"#N canvas.*" (translate-line {:type :patch-header})))
      (is (re-matches #"#X obj.*" (translate-line {:type :node :op "+"}))))))
