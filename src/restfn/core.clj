(ns restfn.core
  (:import (java.io StringWriter PrintWriter)
           (clojure.lang IFn ArityException))
  (:use (clojure [string :only [split]])
        (cheshire [core :only [generate-string]])))

(defn- try-parse-int [s]
  (try (Integer/parseInt s)
    (catch NumberFormatException e s)))

(defprotocol RestSerializable
  (rest-serialize [this]
   "Convert to something Cheshire can JSONify"))

(extend Object
  RestSerializable
  {:rest-serialize identity})

(extend-type clojure.lang.IDeref
  RestSerializable
  (rest-serialize [this]
    (rest-serialize @this)))

(defn- descend-one [obj-raw seg-raw]
  (let [obj (rest-serialize obj-raw)
        seg (try-parse-int seg-raw)]
    (if (instance? IFn obj)
      (obj seg)
      (.get obj seg))))

(defn- do-result [raw-result req]
  (let [result (if (seq? raw-result)
                 (map rest-serialize raw-result)
                 (rest-serialize raw-result))]
    (if (map? result)
      (if-let [action-fn (result (:request-method req))]
        (try (action-fn req)
          (catch ArityException _ (action-fn)))
        result)
      result)))

(defn- exc->str [^Throwable e]
  (let [wrtr (StringWriter.)
        pwrtr (PrintWriter. wrtr)]
    (.printStackTrace e pwrtr)
    (str wrtr)))

(defn get-rest-handler [rest-tree]
  (fn [req]
    (try
      (let [uri      (:uri req)
            uri-segs (remove empty? (split uri #"/"))
            result   (reduce descend-one rest-tree uri-segs)
            body-obj (do-result result req)
            body     (str
                       (generate-string body-obj {:pretty true})
                       \newline)]
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body body})
      (catch Throwable e
        {:status 500
         :headers {"Content-Type" "text/plain"}
         :body (exc->str e)}))))
