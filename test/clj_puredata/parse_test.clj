(ns clj-puredata.parse-test
  (:require [clj-puredata.parse :refer :all]
            [clojure.test :refer :all]))

(deftest basic
  (testing "Parsing"
    (testing "a simple form."
      (is (= (parse [:+ 1 2])
             [{:type :node :op "+" :id 0
               :options {} :args [1 2]}])))
    (testing "recursively, which triggers connections."
      (is (= (parse [:+ [:* 2 2] 1])
             [{:type :node :op "+" :id 0
               :options {} :args [1]}
              {:type :node :op "*" :id 1
               :options {} :args [2 2]}
              {:type :connection
               :from-node {:id 1 :outlet 0}
               :to-node {:id 0 :inlet 0}}])))
    (testing "will skip target inlet when argument is NIL."
      (is (= (parse [:+ nil [:*]])
             [{:type :node :op "+" :id 0
               :options {} :args []}
              {:type :node :op "*" :id 1
               :options {} :args []}
              {:type :connection
               :from-node {:id 1 :outlet 0}
               :to-node {:id 0 :inlet 1}}])))
    (testing "can adjust target inlet."
      (is (= (parse [:+ (inlet-old [:*] 1)])
             [{:type :node :op "+" :id 0
               :options {} :args []}
              {:type :node :op "*" :id 1
               :options {} :args []}
              {:type :connection
               :from-node {:id 1 :outlet 0}
               :to-node {:id 0 :inlet 1}}])))
    (testing "can adjust source outlet."
      (is (= (parse [:+ (outlet-old [:*] 1)])
             [{:type :node :op "+" :id 0
               :options {} :args []}
              {:type :node :op "*" :id 1
               :options {} :args []}
              {:type :connection
               :from-node {:id 1 :outlet 1}
               :to-node {:id 0 :inlet 0}}])))))

#_(deftest tricky
    (testing "Tricky parsing"
      (testing "accomodates the use of LET."
        (is (= (parse (let [x [:* 2 2]] [:+ x x]))
               [{:type :node :op "+" :id 0
                 :options {} :args []}
                {:type :node :op "*" :id 1
                 :options {} :args [2 2]}
                {:type :connection
                 :from-node {:id 1 :outlet 0}
                 :to-node {:id 0 :inlet 0}}
                {:type :connection
                 :from-node {:id 1 :outlet 0}
                 :to-node {:id 0 :inlet 1}}])
            "This might be a bit motivated - does this require a macro that walks the form, distinguishing hiccup and regular clojure? ")
        #_(is (= (pd-patch
                  (let [x (pd [:* 2 2])]
                    (pd [:+ x x]))))
              "This seems more practical - #'pd returns a map (still using unique ids for nodes), constructs a tree with duplicates, and sort them out later."))))

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
    (testing "will assign indices when wrapped in #'WITH-PATCH."
      (is (= (:nodes (with-patch (pd [:+ 1 2 3])))
             [{:type :node
               :op "+" :id 0
               :options {} :args [1 2 3]}])))
    (testing "will assign indices recursively (depth-first, in left-to-right argument order)"
      (is (= (:nodes (with-patch (pd [:+ [:- [:*]] [:/]])))
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
      (is (= (:nodes (with-patch
                (let [x (pd [:+])]
                  (pd [:* x x]))))
             [{:type :node
               :op "*" :id 1
               :options {} :args [{:type :node :op "+" :id 0 :options {} :args []}
                                  {:type :node :op "+" :id 0 :options {} :args []}]}])))))

(deftest walking-the-tree
  (testing "The function WALK-TREE"
    (testing "writes nodes out into the :PATCH field of atom PARSE-CONTEXT."
      (is (= (-> (with-patch (pd [:+]) (pd [:-]))
                 :patch count)
             2)
          #_(= (:patch (with-patch (pd [:+]) (pd [:-])))
               [{:type :node
                 :op "+" :id 0
                 :options {} :args []}
                {:type :node
                 :op "-" :id 1
                 :options {} :args []}])))
    (testing "creates connections when nodes have other nodes as arguments."
      (is (= (-> (:patch (with-patch (pd [:+ [:-]])))
                 (nth 2) :type)
             :connection)
          #_(= (:patch (with-patch (pd [:+ [:-]])))
               [{:type :node
                 :op "+" :id 0
                 :options {} :args []}
                {:type :node
                 :op "-" :id 1
                 :options {} :args []}
                {:type :connection
                 :from-node {:id 1, :outlet 0}
                 :to-node {:id 0, :inlet 0}}])))
    (testing "skips inlet if NIL is supplied as an argument."
      (is (= (-> (:patch (with-patch (pd [:+ nil [:-]])))
                 (nth 2) :to-node :inlet)
             1
             #_[{:type :node
                 :op "+" :id 0
                 :options {} :args [nil]}
                {:type :node
                 :op "-" :id 1
                 :options {} :args []}
                {:type :connection
                 :from-node {:id 1, :outlet 0}
                 :to-node {:id 0, :inlet 1}}])))
    (testing "respects the keys set by #'OUTLET and #'INLET and modifies connections accordingly."
      (is (let [p (:patch (with-patch (pd [:+ (outlet (pd [:*]) 23) (inlet (pd [:/]) 42)])))]
            (and (= (-> p (nth 3) :from-node :outlet) 23)
                 (= (-> p (nth 4) :to-node :inlet) 42)))))))