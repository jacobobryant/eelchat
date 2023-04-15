(ns com.eelchat.app
  (:require [com.biffweb :as biff :refer [q]]
            [com.eelchat.middleware :as mid]
            [com.eelchat.ui :as ui]
            [xtdb.api :as xt]))

(defn app [ctx]
  (ui/app-page
   ctx
   [:p "Select a community, or create a new one."]))

(defn new-community [{:keys [session] :as ctx}]
  (let [comm-id (random-uuid)]
    (biff/submit-tx ctx
      [{:db/doc-type :community
        :xt/id comm-id
        :comm/title (str "Community #" (rand-int 1000))}
       {:db/doc-type :membership
        :mem/user (:uid session)
        :mem/comm comm-id
        :mem/roles #{:admin}}])
    {:status 303
     :headers {"Location" (str "/community/" comm-id)}}))

(defn join-community [{:keys [user community] :as ctx}]
  (biff/submit-tx ctx
    [{:db/doc-type :membership
      :db.op/upsert {:mem/user (:xt/id user)
                     :mem/comm (:xt/id community)}
      :mem/roles [:db/default #{}]}])
  {:status 303
   :headers {"Location" (str "/community/" (:xt/id community))}})

(defn community [{:keys [biff/db user community] :as ctx}]
  (let [member (some (fn [mem]
                       (= (:xt/id community) (get-in mem [:mem/comm :xt/id])))
                     (:user/mems user))]
    (ui/app-page
     ctx
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

(defn wrap-community [handler]
  (fn [{:keys [biff/db path-params] :as ctx}]
    (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
      (handler (assoc ctx :community community))
      {:status 303
       :headers {"location" "/app"}})))

(def plugin
  {:routes ["" {:middleware [mid/wrap-signed-in]}
            ["/app"           {:get app}]
            ["/community"     {:post new-community}]
            ["/community/:id" {:middleware [wrap-community]}
             [""      {:get community}]
             ["/join" {:post join-community}]]]})
