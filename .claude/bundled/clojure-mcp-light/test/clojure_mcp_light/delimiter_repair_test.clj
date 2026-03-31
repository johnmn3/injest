(ns clojure-mcp-light.delimiter-repair-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure-mcp-light.delimiter-repair :as dr]))

(deftest delimiter-error?-test
  (testing "detects no error in valid code"
    (is (false? (dr/delimiter-error? "(def x 1)")))
    (is (false? (dr/delimiter-error? "(defn foo [x] (* x 2))")))
    (is (false? (dr/delimiter-error? "(let [x 1 y 2] (+ x y))"))))

  (testing "detects delimiter errors"
    (is (true? (dr/delimiter-error? "(def x 1")))
    (is (true? (dr/delimiter-error? "(defn foo [x (* x 2))")))
    (is (true? (dr/delimiter-error? "(let [x 1 y 2] (+ x y)"))))

  (testing "handles empty strings"
    (is (false? (dr/delimiter-error? ""))))

  (testing "handles multiple forms"
    (is (false? (dr/delimiter-error? "(def x 1) (def y 2)")))
    (is (true? (dr/delimiter-error? "(def x 1) (def y 2")))))

(deftest fix-delimiters-test
  (testing "returns original string when no errors"
    (is (= "(def x 1)" (dr/fix-delimiters "(def x 1)")))
    (is (= "(defn foo [x] (* x 2))" (dr/fix-delimiters "(defn foo [x] (* x 2))"))))

  (testing "fixes missing closing delimiters"
    (is (= "(def x 1)" (dr/fix-delimiters "(def x 1")))
    (is (= "(+ 1 2 3)" (dr/fix-delimiters "(+ 1 2 3"))))

  (testing "fixes nested delimiter errors"
    (let [result (dr/fix-delimiters "(let [x 1] (+ x 2")]
      (is (string? result))
      (is (false? (dr/delimiter-error? result)))))

  (testing "returns string for valid input"
    (is (string? (dr/fix-delimiters "(def x 1)")))))

(deftest parinfer-repair-test
  (testing "returns success map for fixable code"
    (let [result (dr/parinfer-repair "(def x 1")]
      (is (map? result))
      (is (contains? result :success))
      (is (contains? result :text)))))

(deftest parinferish-repair-test
  (testing "returns success map for fixable code"
    (let [result (dr/parinferish-repair "(def x 1")]
      (is (map? result))
      (is (contains? result :success))
      (is (contains? result :text))
      (is (true? (:success result)))
      (is (= "(def x 1)" (:text result)))))

  (testing "handles complex delimiter errors"
    (let [result (dr/parinferish-repair "(let [x 1\n      y 2\n  (+ x y))")]
      (is (true? (:success result)))
      (is (= "(let [x 1\n      y 2]\n  (+ x y))" (:text result)))))

  (testing "handles nested missing delimiters"
    (let [result (dr/parinferish-repair "(defn baz [x]\n  (let [y (* x 2]\n    (+ y 1)))")]
      (is (true? (:success result)))
      (is (false? (dr/delimiter-error? (:text result))))))

  (testing "returns error map on failure"
    (let [result (dr/parinferish-repair nil)]
      (is (map? result))
      (is (contains? result :success))
      (is (false? (:success result)))
      (is (contains? result :error)))))

(deftest repair-delimiters-test
  (testing "returns success map for fixable code"
    (let [result (dr/repair-delimiters "(def x 1")]
      (is (map? result))
      (is (contains? result :success))
      (is (contains? result :text))
      (is (true? (:success result)))))

  (testing "works with complex nested errors"
    (let [result (dr/repair-delimiters "(defn foo [x]\n  (let [y (* x 2]\n    (+ y 1)))")]
      (is (true? (:success result)))
      (is (false? (dr/delimiter-error? (:text result))))))

  (testing "returns repaired text that passes delimiter check"
    (let [result (dr/repair-delimiters "(+ 1 2 3")]
      (is (true? (:success result)))
      (is (= "(+ 1 2 3)" (:text result))))))

(deftest delimiter-error-with-function-literals-test
  (testing "does not error on valid function literals"
    (is (false? (dr/delimiter-error? "#(+ % 1)")))
    (is (false? (dr/delimiter-error? "(map #(* % 2) [1 2 3])")))
    (is (false? (dr/delimiter-error? "(filter #(> % 10) nums)"))))

  (testing "detects delimiter errors in code with function literals"
    (is (true? (dr/delimiter-error? "#(+ % 1")))
    (is (true? (dr/delimiter-error? "(map #(* % 2) [1 2 3")))
    (is (true? (dr/delimiter-error? "(filter #(> % 10 nums)")))))

(deftest delimiter-error-with-regex-test
  (testing "does not error on valid regex literals"
    (is (false? (dr/delimiter-error? "#\"pattern\"")))
    (is (false? (dr/delimiter-error? "(re-find #\"[0-9]+\" s)")))
    (is (false? (dr/delimiter-error? "#\"\\s+\""))))

  (testing "detects delimiter errors in code with regex literals"
    (is (true? (dr/delimiter-error? "(re-find #\"[0-9]+\" s")))
    (is (true? (dr/delimiter-error? "(if (re-matches #\"test\" x) true")))))

(deftest delimiter-error-with-quotes-test
  (testing "does not error on valid quoted forms"
    (is (false? (dr/delimiter-error? "'(1 2 3)")))
    (is (false? (dr/delimiter-error? "`(foo ~bar)")))
    (is (false? (dr/delimiter-error? "(quote (a b c))"))))

  (testing "detects delimiter errors in code with quotes"
    (is (true? (dr/delimiter-error? "'(1 2 3")))
    (is (true? (dr/delimiter-error? "`(foo ~bar")))))

(deftest fix-delimiters-with-function-literals-test
  (testing "fixes delimiter errors in code with function literals"
    (let [result (dr/fix-delimiters "(map #(* % 2) [1 2 3")]
      (is (string? result))
      (is (false? (dr/delimiter-error? result)))))

  (testing "preserves function literals when fixing"
    (let [code "(defn process [xs] (map #(inc %) xs"
          fixed (dr/fix-delimiters code)]
      (is (string? fixed))
      (is (false? (dr/delimiter-error? fixed)))
      (is (re-find #"#\(" fixed)))) ; Function literal preserved

  (testing "returns original when no errors in code with function literals"
    (is (= "(map #(+ % 1) [1 2 3])"
           (dr/fix-delimiters "(map #(+ % 1) [1 2 3])")))))

(deftest delimiter-error-with-deref-test
  (testing "does not error on valid deref forms"
    (is (false? (dr/delimiter-error? "@foo")))
    (is (false? (dr/delimiter-error? "(swap! @atom inc)")))
    (is (false? (dr/delimiter-error? "@(future (+ 1 2))"))))

  (testing "detects delimiter errors in code with deref"
    (is (true? (dr/delimiter-error? "(swap! @atom inc")))
    (is (true? (dr/delimiter-error? "@(future (+ 1 2")))))

(deftest delimiter-error-with-var-test
  (testing "does not error on valid var forms"
    (is (false? (dr/delimiter-error? "#'foo")))
    (is (false? (dr/delimiter-error? "(alter-var-root #'foo inc)")))
    (is (false? (dr/delimiter-error? "#'clojure.core/+"))))

  (testing "detects delimiter errors in code with var"
    (is (true? (dr/delimiter-error? "(alter-var-root #'foo inc")))
    (is (true? (dr/delimiter-error? "(let [x #'foo] (x 1 2")))))

(deftest delimiter-error-with-reader-conditionals-test
  (testing "does not error on valid reader conditional forms"
    (is (false? (dr/delimiter-error? "#?(:clj 1 :cljs 2)")))
    (is (false? (dr/delimiter-error? "#?@(:clj [1 2] :cljs [3 4])")))
    (is (false? (dr/delimiter-error? "[1 2 #?(:clj 3)]"))))

  (testing "detects delimiter errors in surrounding code with reader conditionals"
    (is (true? (dr/delimiter-error? "(let [x #?(:clj 1 :cljs 2)] x")))
    (is (true? (dr/delimiter-error? "[1 2 #?(:clj 3) 4")))))

(deftest delimiter-error-with-reader-conditional-splicing-test
  (testing "parses reader conditional splicing with known features"
    (is (false? (dr/delimiter-error? "{1 2 #?@(:clj [3 4])}")))
    (is (false? (dr/delimiter-error? "{1 2 #?@(:cljs [3 4])}")))
    (is (false? (dr/delimiter-error? "{1 2 #?@(:bb [3 4])}")))
    (is (false? (dr/delimiter-error? "(def x [1 2 #?@(:clj [3 4])])"))))

  (testing "parses reader conditional splicing with unknown features"
    (is (false? (dr/delimiter-error? "{1 2 #?@(:my-custom-feature [3 4])}")))
    (is (false? (dr/delimiter-error? "{1 2 #?@(:unknown [3 4])}")))
    (is (false? (dr/delimiter-error? "(def x [1 2 #?@(:custom-lang [3 4])])"))))

  (testing "detects delimiter errors with reader conditional splicing and known features"
    (is (true? (dr/delimiter-error? "{1 2 #?@(:clj [3 4]")))
    (is (true? (dr/delimiter-error? "{1 2 #?@(:cljs [3 4)")))
    (is (true? (dr/delimiter-error? "(def x [1 2 #?@(:clj [3 4])]"))))

  (testing "handles real-world Transit reader map with splicing"
    (is (false? (dr/delimiter-error?
                 "{\"DateTime\" (transit/read-handler read-date-time)
                   \"Date\" (transit/read-handler read-local-date)
                   \"AVLMap\" (transit/read-handler #(into (avl/sorted-map) %))
                   #?@(:cljs [\"u\" cljs.core/uuid])}"))))

  (testing "detects delimiter errors in Transit reader map"
    (is (true? (dr/delimiter-error?
                "{\"DateTime\" (transit/read-handler read-date-time)
                  \"Date\" (transit/read-handler read-local-date)
                  #?@(:cljs [\"u\" cljs.core/uuid]"))))

  (testing "handles multiple reader conditional splicing forms"
    (is (false? (dr/delimiter-error?
                 "{:a 1
                   #?@(:clj [:b 2])
                   #?@(:cljs [:c 3])
                   :d 4}")))
    (is (false? (dr/delimiter-error?
                 "[1 2 #?@(:bb [3 4]) 5 #?@(:clj [6 7])]"))))

  (testing "known limitation: missing ) on reader conditional with unknown feature at EOF"
    ;; Specific edge case: when the reader conditional form itself (#?@(...))
    ;; is missing its closing ) at EOF and the feature is unknown, edamame
    ;; doesn't provide delimiter info (though it still reports an error).
    ;; Use actual-delimiter-error? to avoid logging the expected error.
    (is (false? (dr/actual-delimiter-error? "{1 2 #?@(:unknown [3 4]")))
    ;; But delimiter errors within the vector ARE detected even with unknown features:
    (is (true? (dr/actual-delimiter-error? "{1 2 #?@(:unknown [3 4")))
    ;; And errors with known features provide full delimiter info:
    (is (true? (dr/actual-delimiter-error? "{1 2 #?@(:cljs [3 4]")))
    (is (true? (dr/actual-delimiter-error? "{1 2 #?@(:cljs [3 4")))))

(deftest delimiter-error-with-metadata-test
  (testing "does not error on valid metadata forms"
    (is (false? (dr/delimiter-error? "^:private foo")))
    (is (false? (dr/delimiter-error? "^{:doc \"test\"} bar")))
    (is (false? (dr/delimiter-error? "(defn ^:private foo [] 1)"))))

  (testing "detects delimiter errors in code with metadata"
    (is (true? (dr/delimiter-error? "^{:doc \"test\"} (defn foo [] 1")))
    (is (true? (dr/delimiter-error? "(defn ^:private foo [] 1")))))

(deftest delimiter-error-comprehensive-test
  (testing "handles complex real-world Clojure code without errors"
    (is (false? (dr/delimiter-error?
                 "(ns foo.bar
                     (:require [clojure.string :as str]))

                   (defn ^:private process [data]
                     (let [result @(future
                                     (map #(* % 2)
                                          (filter #(> % 10) data)))]
                       (when-let [x (first result)]
                         #?(:clj (str/upper-case x)
                            :cljs (.toUpperCase x)))))"))))

  (testing "detects delimiter errors in complex nested code"
    (is (true? (dr/delimiter-error?
                "(ns foo.bar
                    (:require [clojure.string :as str]))

                  (defn process [data]
                    (let [result @(future
                                    (map #(* % 2)
                                         (filter #(> % 10) data)))]
                      (when-let [x (first result)]
                        (str/upper-case x"))))) ; Missing multiple closing parens

(deftest delimiter-error-with-data-readers-test
  (testing "does not error on standard EDN data readers"
    (is (false? (dr/delimiter-error? "#inst \"2023-01-01\"")))
    (is (false? (dr/delimiter-error? "#uuid \"550e8400-e29b-41d4-a716-446655440000\"")))
    (is (false? (dr/delimiter-error? "(def x #inst \"2023-01-01\")")))
    (is (false? (dr/delimiter-error? "(def y #uuid \"550e8400-e29b-41d4-a716-446655440000\")")))
    (is (false? (dr/delimiter-error? "#my/custom {:a 1}")))
    (is (false? (dr/delimiter-error? "(def z #custom/tag \"value\")"))))

  (testing "handles known ClojureScript data readers"
    (is (false? (dr/delimiter-error? "#js {:x 1 :y 2}"))))

  (testing "detects delimiter errors in code with data readers"
    (is (true? (dr/delimiter-error? "(def x #inst \"2023-01-01\"")))
    (is (true? (dr/delimiter-error? "(let [t #uuid \"550e8400-e29b-41d4-a716-446655440000\"] t")))
    (is (true? (dr/delimiter-error? "(def x #my/custom {:a 1")))))

(deftest clojurescript-tagged-literals-test
  (testing "handles #js tagged literals"
    (is (false? (dr/delimiter-error? "#js {:foo 1}")))
    (is (false? (dr/delimiter-error? "#js [1 2 3]")))
    (is (false? (dr/delimiter-error? "(def obj #js {:x 1 :y 2})")))
    (is (false? (dr/delimiter-error? "(defn foo [] #js {:bar \"baz\"})"))))

  (testing "handles #jsx tagged literals"
    (is (false? (dr/delimiter-error? "#jsx [:div \"hello\"]")))
    (is (false? (dr/delimiter-error? "(defn component [] #jsx [:div {:class \"foo\"} \"text\"])"))))

  (testing "handles #queue tagged literals"
    (is (false? (dr/delimiter-error? "#queue [1 2 3]")))
    (is (false? (dr/delimiter-error? "(def q #queue [])"))))

  (testing "handles #date tagged literals"
    (is (false? (dr/delimiter-error? "#date \"2024-01-01\"")))
    (is (false? (dr/delimiter-error? "(def d #date \"2024-12-31\")"))))

  (testing "detects delimiter errors with tagged literals"
    (is (true? (dr/delimiter-error? "(def obj #js {:x 1")))
    (is (true? (dr/delimiter-error? "#js [1 2 3")))
    (is (true? (dr/delimiter-error? "(defn foo [] #jsx [:div \"hello\"")))
    (is (true? (dr/delimiter-error? "(def q #queue [1 2 3"))))

  (testing "handles nested tagged literals"
    (is (false? (dr/delimiter-error? "#js {:a #js [1 2 3]}")))
    (is (false? (dr/delimiter-error? "(def data #js {:nested #js {:deep true}})")))
    (is (true? (dr/delimiter-error? "#js {:a #js [1 2 3")))
    (is (true? (dr/delimiter-error? "(def data #js {:nested #js {:deep true}"))))

  (testing "handles multiple tagged literals in same form"
    (is (false? (dr/delimiter-error? "(def data [#js {:x 1} #date \"2024-01-01\" #queue [1]])")))
    (is (true? (dr/delimiter-error? "(def data [#js {:x 1} #date \"2024-01-01\" #queue [1]")))))

(deftest clojurescript-features-test
  (testing "handles namespaced keywords"
    (is (false? (dr/delimiter-error? "::foo")))
    (is (false? (dr/delimiter-error? "::foo/bar")))
    (is (false? (dr/delimiter-error? "{::id 1 ::name \"test\"}")))
    (is (false? (dr/delimiter-error? "(defn foo [x] {::result (* x 2)})"))))

  (testing "detects delimiter errors with namespaced keywords"
    (is (true? (dr/delimiter-error? "{::id 1 ::name \"test\"")))
    (is (true? (dr/delimiter-error? "(defn foo [x] {::result (* x 2})"))))

  (testing "handles ClojureScript destructuring with namespaced keywords"
    (is (false? (dr/delimiter-error? "(let [{::keys [foo bar]} data] foo)")))
    (is (false? (dr/delimiter-error? "(defn process [{::keys [id name]}] id)"))))

  (testing "detects delimiter errors in destructuring"
    (is (true? (dr/delimiter-error? "(let [{::keys [foo bar]} data] foo")))
    (is (true? (dr/delimiter-error? "(defn process [{::keys [id name]} id")))))

(deftest mixed-clj-cljs-features-test
  (testing "handles mixed Clojure and ClojureScript features"
    (is (false? (dr/delimiter-error?
                 "(ns app.core
                     (:require [clojure.string :as str]))

                   (defn process [data]
                     (let [obj #js {:name \"test\"}
                           result (map #(* % 2) data)
                           date #date \"2024-01-01\"]
                       {::obj obj
                        ::result result
                        ::date date}))"))))

  (testing "detects delimiter errors in mixed code"
    (is (true? (dr/delimiter-error?
                "(ns app.core
                    (:require [clojure.string :as str]))

                  (defn process [data]
                    (let [obj #js {:name \"test\"}
                          result (map #(* % 2) data]
                      {::obj obj
                       ::result result"))))

  (testing "handles reader conditionals with tagged literals"
    (is (false? (dr/delimiter-error?
                 "#?(:clj {:type :jvm}
                      :cljs #js {:type \"browser\"})")))
    (is (false? (dr/delimiter-error?
                 "(def config #?(:clj  (read-string slurp \"config.edn\")
                                  :cljs #js {:env \"dev\"}))"))))

  (testing "detects delimiter errors in reader conditionals with tagged literals"
    (is (true? (dr/delimiter-error?
                "#?(:clj {:type :jvm}
                     :cljs #js {:type \"browser\")")))
    (is (true? (dr/delimiter-error?
                "(def config #?(:clj (read-string slurp \"config.edn\")
                                 :cljs #js {:env \"dev\"")))))
