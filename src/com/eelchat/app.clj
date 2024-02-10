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
  (let [community-id (random-uuid)]
    (biff/submit-tx ctx
      [{:db/doc-type :community
        :xt/id community-id
        :community/title (str "Community #" (rand-int 1000))}
       {:db/doc-type :membership
        :membership/user (:uid session)
        :membership/community community-id
        :membership/roles #{:admin}}])
    {:status 303
     :headers {"Location" (str "/community/" community-id)}}))

(defn join-community [{:keys [user community] :as ctx}]
  (biff/submit-tx ctx
    [{:db/doc-type :membership
      :db.op/upsert {:membership/user (:xt/id user)
                     :membership/community (:xt/id community)}
      :membership/roles [:db/default #{}]}])
  {:status 303
   :headers {"Location" (str "/community/" (:xt/id community))}})

(defn community [{:keys [biff/db path-params] :as ctx}]
  (if (some? (xt/entity db (parse-uuid (:id path-params))))
    (ui/app-page
     ctx
     [:.border.border-neutral-600.p-3.bg-white.grow
      "Messages window"]
     [:.h-3]
     [:.border.border-neutral-600.p-3.h-28.bg-white
      "Compose window"])
    {:status 303
     :headers {"location" "/app"}}))

(defn community [{:keys [biff/db user community] :as ctx}]
  (let [member (some (fn [membership]
                       (= (:xt/id community) (get-in membership [:membership/community :xt/id])))
                     (:user/memberships user))]
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
        [:h1.text-3xl.text-center (:community/title community)]
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

(def module
  {:routes ["" {:middleware [mid/wrap-signed-in]}
            ["/app"           {:get app}]
            ["/community"     {:post new-community}]
            ["/community/:id" {:middleware [wrap-community]}
             [""      {:get community}]
             ["/join" {:post join-community}]]]})
