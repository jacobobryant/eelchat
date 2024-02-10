(ns com.eelchat.subscriptions
  (:require [com.biffweb :as biff :refer [q]]
            [remus :as remus])
  (:import [org.jsoup Jsoup]))

(defn every-n-minutes [n]
  (iterate #(biff/add-seconds % (* n 60)) (java.util.Date.)))

(defn subscriptions-to-update [db]
  (q db
     '{:find (pull subscription [*])
       :in [t]
       :where [[subscription :subscription/url]
               [(get-attr subscription :subscription/fetched-at #inst "1970")
                [fetched-at ...]]
               [(<= fetched-at t)]]}
     (biff/add-seconds (java.util.Date.) (* -60 30))))

(defn assoc-result [{:keys [biff/base-url]} {:subscription/keys [url last-modified etag] :as subscription}]
  (assoc subscription ::result (biff/catchall-verbose
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

(defn subscription-tx [{:subscription/keys [channel last-post-uri] :keys [xt/id ::result]}]
  (let [post (-> result :feed :entries first)
        uri ((some-fn :uri :link) post)]
    (concat [(biff/assoc-some
              {:db/doc-type :subscription
               :db/op :update
               :xt/id id
               :subscription/fetched-at :db/now}
              :subscription/last-post-uri uri
              :subscription/last-modified (get-in result [:response :headers "Last-Modified"])
              :subscription/etag (get-in result [:response :headers "Etag"]))]
            (when (and (some? uri) (not= uri last-post-uri))
              [{:db/doc-type :message
                :message/membership :system
                :message/channel channel
                :message/created-at :db/now
                :message/text (format-post post)}]))))

(defn fetch-rss [{:keys [biff/db] :as ctx}]
  (doseq [subscription (subscriptions-to-update db)]
    (biff/submit-job ctx :fetch-rss subscription)))

(defn fetch-rss-consumer [{:keys [biff/job] :as ctx}]
  (biff/submit-tx ctx
    (subscription-tx (assoc-result ctx job))))

(def module
  {:tasks [{:task #'fetch-rss
            :schedule #(every-n-minutes 5)}]
   :queues [{:id :fetch-rss
             :consumer #'fetch-rss-consumer}]})
