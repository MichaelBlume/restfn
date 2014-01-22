(ns restfn.test
  (:import (java.util ArrayList))
  (:use (clojure test)
        (clojure [string :only [split-lines]])
        (restfn core)
        (cheshire [core :only [parse-string]])))

(extend-type java.util.regex.Pattern
  RestSerializable
  (rest-serialize [r] #(re-matches r %)))

(def handler
  (get-rest-handler
    {"list" [1 3 5]
     "map" {"foo" "bar"}
     "atom" (atom 5)
     "doubleatom" (atom (atom 7))
     "javalist" (ArrayList. [5 9 7])
     "simplefn" inc
     "complexfn" (fn [x] {"key" x})
     "pattern" #"(.*)and(.*)"
     "seq" (for [x (range 3)]
             (reify RestSerializable (rest-serialize [_] x)))
     "rest" {:post (fn [req] (:req-key req))
             :delete (fn [] "deleted")}}))

(defn object-from-request [req]
  (let [res (handler req)]
    (is (= (:status res) 200) res)
    (is (= (:headers res) {"Content-Type" "application/json"}) res)
    (parse-string (:body (handler req)))))

(defn object-from-uri [uri]
  (object-from-request {:uri uri :request-method :get}))

(deftest simplegets
  (are [uri result]
    (= result (object-from-uri uri))
    "list" [1 3 5]
    "/list/" [1 3 5]
    "/list/0" 1
    "/list/1" 3
    "atom" 5
    "doubleatom" 7
    "map" {"foo" "bar"}
    "map/foo" "bar"
    "javalist/" [5 9 7]
    "javalist/0" 5
    "simplefn/5" 6
    "complexfn/3" {"key" 3}
    "complexfn/hello/key" "hello"
    "pattern/fooandbar" ["fooandbar" "foo" "bar"]
    "seq" [0 1 2]))

(deftest test-404
  (is (= 404 (:status (handler {:uri "/null" :request-method :get})))))

(deftest testrest
  (are [req result]
    (= result (object-from-request req))
    {:uri            "rest"
     :request-method :post
     :req-key        [1 2 3]} [1 2 3]
    {:uri            "rest"
     :request-method :delete} "deleted"))

(deftest testerror
  (let [result (handler {:uri "list/5" :request-method :get})]
    (is (= (:status result) 500))
    (is (= (:headers result) {"Content-Type" "text/plain"}))
    (is (re-matches #".*IndexOutOfBoundsException.*"
                    ((split-lines (:body result)) 0)))))
