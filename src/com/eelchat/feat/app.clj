(ns com.eelchat.feat.app
  (:require [com.biffweb :as biff :refer [q]]
            [com.eelchat.middleware :as mid]
            [com.eelchat.ui :as ui]
            [xtdb.api :as xt]))

(defn app [req]
  (ui/app-page
   req
   [:p "Select a community, or create a new one."]))

(defn new-community [{:keys [session] :as req}]
  (let [comm-id (random-uuid)]
    (biff/submit-tx req
      [{:db/doc-type :community
        :xt/id comm-id
        :comm/title (str "Community #" (rand-int 1000))}
       {:db/doc-type :membership
        :mem/user (:uid session)
        :mem/comm comm-id
        :mem/roles #{:admin}}])
    {:status 303
     :headers {"Location" (str "/community/" comm-id)}}))

(defn join-community [{:keys [user community] :as req}]
  (biff/submit-tx req
    [{:db/doc-type :membership
      :db.op/upsert {:mem/user (:xt/id user)
                     :mem/comm (:xt/id community)}
      :mem/roles [:db/default #{}]}])
  {:status 303
   :headers {"Location" (str "/community/" (:xt/id community))}})

(defn new-channel [{:keys [community roles] :as req}]
  (if (and community (contains? roles :admin))
    (let [chan-id (random-uuid)]
     (biff/submit-tx req
       [{:db/doc-type :channel
         :xt/id chan-id
         :chan/title (str "Channel #" (rand-int 1000))
         :chan/comm (:xt/id community)}])
     {:status 303
      :headers {"Location" (str "/community/" (:xt/id community) "/channel/" chan-id)}})
    {:status 403
     :body "Forbidden."}))

(defn delete-channel [{:keys [channel roles] :as req}]
  (when (contains? roles :admin)
    (biff/submit-tx req
      [{:db/op :delete
        :xt/id (:xt/id channel)}]))
  [:<>])

(defn community [{:keys [biff/db user community] :as req}]
  (let [member (some (fn [mem]
                       (= (:xt/id community) (get-in mem [:mem/comm :xt/id])))
                     (:user/mems user))]
    (ui/app-page
     req
     (if member
       [:<>
        [:.border.border-neutral-600.p-3.bg-white.grow
         "Messages window"]
        [:.h-3]
        [:.border.border-neutral-600.p-3.h-28.bg-white
         "Compose window"]]
       [:<>
        [:.grow]
        [:h1.text-3xl.text-center (:comm/title community)]
        [:.h-6]
        (biff/form
         {:action (str "/community/" (:xt/id community) "/join")
          :class "flex justify-center"}
         [:button.btn {:type "submit"} "Join this community"])
        [:div {:class "grow-[1.75]"}]]))))

(defn channel-page [req]
  ;; We'll update this soon
  (community req))

(defn wrap-community [handler]
  (fn [{:keys [biff/db user path-params] :as req}]
    (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
      (let [roles (->> (:user/mems user)
                       (filter (fn [mem]
                                 (= (:xt/id community) (get-in mem [:mem/comm :xt/id]))))
                       first
                       :mem/roles)]
        (handler (assoc req :community community :roles roles)))
      {:status 303
       :headers {"location" "/app"}})))

(defn wrap-channel [handler]
  (fn [{:keys [biff/db user community path-params] :as req}]
    (let [channel (xt/entity db (parse-uuid (:chan-id path-params)))]
      (if (= (:chan/comm channel) (:xt/id community))
        (handler (assoc req :channel channel))
        {:status 303
         :headers {"Location" (str "/community/" (:xt/id community))}}))))

(def features
  {:routes ["" {:middleware [mid/wrap-signed-in]}
            ["/app"           {:get app}]
            ["/community"     {:post new-community}]
            ["/community/:id" {:middleware [wrap-community]}
             [""      {:get community}]
             ["/join" {:post join-community}]
             ["/channel" {:post new-channel}]
             ["/channel/:chan-id" {:middleware [wrap-channel]}
              ["" {:get channel-page
                   :delete delete-channel}]]]]})
