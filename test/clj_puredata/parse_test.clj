(ns clj-puredata.parse-test
  (:require [clj-puredata.parse :refer :all]
            [clojure.test :refer :all]))

(deftest constructing-the-tree
  (testing "The function PD"
    (testing "will expand hiccup [:+ 1 2] to maps {:type ::node ...}."
      (is (= (pd [:+ 1 2 3])
             {:type :node
              :op "+" :id -1
              :options {} :args [1 2 3]})))
    (testing "will pass along maps in second position as options."
      (is (= (pd [:+ {:an-option true} 1])
             {:type :node
              :op "+" :id -1
              :options {:an-option true} :args [1]})))
    (testing "also works recursively."
      (is (= (pd [:+ [:- 3 2] 1])
             {:type :node
              :op "+" :id -1
              :options {} :args [{:type :node
                                  :op "-" :id -1
                                  :options {} :args [3 2]}
                                 1]})))
    (testing "will assign indices when wrapped in #'IN-CONTEXT."
      (is (= (:nodes (in-context (pd [:+ 1 2 3])))
             [{:type :node
               :op "+" :id 0
               :options {} :args [1 2 3]}])))
    (testing "will assign indices recursively (depth-first, in left-to-right argument order)"
      (is (= (:nodes (in-context (pd [:+ [:- [:*]] [:/]])))
             [{:type :node
               :op "+" :id 0
               :options {} :args [{:type :node
                                   :op "-" :id 1
                                   :options {} :args [{:type :node
                                                       :op "*" :id 2
                                                       :options {} :args []}]}
                                  {:type :node
                                   :op "/" :id 3
                                   :options {} :args []}]}])))
    (testing "intentionally preserves the indices of duplicate nodes in tree."
      (is (= (:nodes (in-context
                (let [x (pd [:+])]
                  (pd [:* x x]))))
             [{:type :node
               :op "*" :id 1
               :options {} :args [{:type :node :op "+" :id 0 :options {} :args []}
                                  {:type :node :op "+" :id 0 :options {} :args []}]}])))))

(deftest walking-the-tree
  (testing "The function WALK-TREE"
    (testing "writes nodes out into the :LINES field of atom PARSE-CONTEXT."
      (is (= (-> (in-context (pd [:+]) (pd [:-]))
                 :lines count)
             2)))
    (testing "creates connections when nodes have other nodes as arguments."
      (is (= (-> (:lines (in-context (pd [:+ [:-]])))
                 (nth 2) :type)
             :connection)))
    (testing "skips inlet if NIL is supplied as an argument."
      (is (= (-> (:lines (in-context (pd [:+ nil [:-]])))
                 (nth 2) :to-node :inlet)
             1)))
    (testing "respects the keys set by #'OUTLET and #'INLET and modifies connections accordingly."
      (is (let [p (:lines (in-context (pd [:+ (outlet (pd [:*]) 23) (inlet (pd [:/]) 42)])))]
            (and (= (-> p (nth 3) :from-node :outlet) 23)
                 (= (-> p (nth 4) :to-node :inlet) 42)))))))
