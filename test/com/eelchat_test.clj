(ns com.eelchat-test
  ;; If you add more test files, require them here so that they'll get loaded by com.eelchat/on-save
  (:require [cheshire.core :as cheshire]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [com.biffweb :as biff :refer [test-xtdb-node]]
            [com.eelchat :as main]
            [com.eelchat.app :as app]
            [malli.generator :as mg]
            [rum.core :as rum]
            [xtdb.api :as xt]))

(deftest example-test
  (is (= 4 (+ 2 2))))

(defn get-context [node]
  {:biff.xtdb/node  node
   :biff/db         (xt/db node)
   :biff/malli-opts #'main/malli-opts})
