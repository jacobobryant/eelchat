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

(defn community [{:keys [biff/db path-params] :as req}]
  (if (some? (xt/entity db (parse-uuid (:id path-params))))
    (ui/app-page
     req
     [:.border.border-neutral-600.p-3.bg-white.grow
      "Messages window"]
     [:.h-3]
     [:.border.border-neutral-600.p-3.h-28.bg-white
      "Compose window"])
    {:status 303
     :headers {"location" "/app"}}))

(def features
  {:routes ["" {:middleware [mid/wrap-signed-in]}
            ["/app"           {:get app}]
            ["/community"     {:post new-community}]
            ["/community/:id" {:get community}]]})
