(ns com.eelchat.feat.subscriptions
  (:require [com.biffweb :as biff :refer [q]]
            [remus :as remus])
  (:import [org.jsoup Jsoup]))

(defn every-n-minutes [n]
  (iterate #(biff/add-seconds % (* n 60)) (java.util.Date.)))

(defn subs-to-update [db]
  (q db
     '{:find (pull sub [*])
       :in [t]
       :where [[sub :sub/url]
               [(get-attr sub :sub/fetched-at #inst "1970")
                [fetched-at ...]]
               [(<= fetched-at t)]]}
     (biff/add-seconds (java.util.Date.) (* -60 30))))

(defn assoc-result [{:keys [biff/base-url]} {:sub/keys [url last-modified etag] :as sub}]
  (assoc sub ::result (biff/catchall-verbose
                       (remus/parse-url
                        url
                        {:headers (biff/assoc-some
                                   {"User-Agent" base-url}
                                   "If-None-Match" etag
                                   "If-Modified-Since" last-modified)
                         :socket-timeout 5000
                         :connection-timeout 5000}))))

(defn format-post [{:keys [title author published-date updated-date link contents]}]
  (let [text-body (some-> contents
                          first
                          :value
                          (Jsoup/parse)
                          (.text))
        text-body (if (and text-body (< 300 (count text-body)))
                    (str (subs text-body 0 300) "...")
                    text-body)]
    (str title " | " author " | " (or published-date updated-date) "\n"
         text-body "\n"
         link)))

(defn sub-tx [{:sub/keys [chan last-post-uri] :keys [xt/id ::result]}]
  (let [post (-> result :feed :entries first)
        uri ((some-fn :uri :link) post)]
    (concat [(biff/assoc-some
              {:db/doc-type :subscription
               :db/op :update
               :xt/id id
               :sub/fetched-at :db/now}
              :sub/last-post-uri uri
              :sub/last-modified (get-in result [:response :headers "Last-Modified"])
              :sub/etag (get-in result [:response :headers "Etag"]))]
            (when (and (some? uri) (not= uri last-post-uri))
              [{:db/doc-type :message
                :msg/mem :system
                :msg/channel chan
                :msg/created-at :db/now
                :msg/text (format-post post)}]))))

(defn fetch-rss [{:keys [biff/db] :as sys}]
  (doseq [sub (subs-to-update db)]
    (biff/submit-job sys :fetch-rss sub)))

(defn fetch-rss-consumer [{:keys [biff/job] :as sys}]
  (biff/submit-tx sys
    (sub-tx (assoc-result sys job))))

(def features
  {:tasks [{:task #'fetch-rss
            :schedule #(every-n-minutes 5)}]
   :queues [{:id :fetch-rss
             :consumer #'fetch-rss-consumer}]})
