(ns com.eelchat.app
  (:require [com.biffweb :as biff :refer [q]]
            [com.eelchat.middleware :as mid]
            [com.eelchat.ui :as ui]
            [ring.adapter.jetty9 :as jetty]
            [rum.core :as rum]
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

(defn new-channel [{:keys [community roles] :as ctx}]
  (if (and community (contains? roles :admin))
    (let [channel-id (random-uuid)]
     (biff/submit-tx ctx
       [{:db/doc-type :channel
         :xt/id channel-id
         :channel/title (str "Channel #" (rand-int 1000))
         :channel/community (:xt/id community)}])
     {:status 303
      :headers {"Location" (str "/community/" (:xt/id community) "/channel/" channel-id)}})
    {:status 403
     :body "Forbidden."}))

(defn delete-channel [{:keys [biff/db channel roles] :as ctx}]
  (when (contains? roles :admin)
    (biff/submit-tx ctx
      (for [id (conj (q db
                        '{:find message
                          :in [channel]
                          :where [[message :message/channel channel]]}
                        (:xt/id channel))
                     (:xt/id channel))]
        {:db/op :delete
         :xt/id id})))
  [:<>])

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

(defn message-view [{:message/keys [membership text created-at]}]
  (let [username (str "User " (subs (str membership) 0 4))]
    [:div
     [:.text-sm
      [:span.font-bold username]
      [:span.w-2.inline-block]
      [:span.text-gray-600 (biff/format-date created-at "d MMM h:mm aa")]]
     [:p.whitespace-pre-wrap.mb-6 text]]))

(defn new-message [{:keys [channel membership params] :as ctx}]
  (let [message {:xt/id (random-uuid)
             :message/membership (:xt/id membership)
             :message/channel (:xt/id channel)
             :message/created-at (java.util.Date.)
             :message/text (:text params)}]
    (biff/submit-tx (assoc ctx :biff.xtdb/retry false)
      [(assoc message :db/doc-type :message)])
    [:<>]))

(defn channel-page [{:keys [biff/db community channel] :as ctx}]
  (let [messages (q db
                    '{:find (pull message [*])
                      :in [channel]
                      :where [[message :message/channel channel]]}
                    (:xt/id channel))
        href (str "/community/" (:xt/id community)
                  "/channel/" (:xt/id channel))]
    (ui/app-page
     ctx
     [:.border.border-neutral-600.p-3.bg-white.grow.flex-1.overflow-y-auto#messages
      {:hx-ext "ws"
       :ws-connect (str href "/connect")
       :_ "on load or newMessage set my scrollTop to my scrollHeight"}
      (map message-view (sort-by :message/created-at messages))]
      [:.h-3]
      (biff/form
       {:hx-post href
        :hx-target "#messages"
        :hx-swap "beforeend"
        :_ (str "on htmx:afterRequest"
                " set <textarea/>'s value to ''"
                " then send newMessage to #messages")
        :class "flex"}
       [:textarea.w-full#text {:name "text"}]
       [:.w-2]
       [:button.btn {:type "submit"} "Send"]))))

(defn connect [{:keys [com.eelchat/chat-clients] {channel-id :xt/id} :channel :as ctx}]
  {:status 101
   :headers {"upgrade" "websocket"
             "connection" "upgrade"}
   :ws {:on-connect (fn [ws]
                      (swap! chat-clients update channel-id (fnil conj #{}) ws))
        :on-close (fn [ws status-code reason]
                    (swap! chat-clients
                           (fn [chat-clients]
                             (let [chat-clients (update chat-clients channel-id disj ws)]
                               (cond-> chat-clients
                                 (empty? (get chat-clients channel-id)) (dissoc channel-id))))))}})

(defn on-new-message [{:keys [biff.xtdb/node com.eelchat/chat-clients]} tx]
  (let [db-before (xt/db node {::xt/tx-id (dec (::xt/tx-id tx))})]
    (doseq [[op & args] (::xt/tx-ops tx)
            :when (= op ::xt/put)
            :let [[doc] args]
            :when (and (contains? doc :message/text)
                       (nil? (xt/entity db-before (:xt/id doc))))
            :let [html (rum/render-static-markup
                        [:div#messages {:hx-swap-oob "beforeend"}
                         (message-view doc)
                         [:div {:_ "init send newMessage to #messages then remove me"}]])]
            ws (get @chat-clients (:message/channel doc))]
      (jetty/send! ws html))))

(defn wrap-community [handler]
  (fn [{:keys [biff/db user path-params] :as ctx}]
    (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
      (let [membership (->> (:user/memberships user)
                            (filter (fn [membership]
                                      (= (:xt/id community) (get-in membership [:membership/community :xt/id]))))
                            first)
            roles (:membership/roles membership)]
        (handler (assoc ctx :community community :roles roles :membership membership)))
      {:status 303
       :headers {"location" "/app"}})))

(defn wrap-channel [handler]
  (fn [{:keys [biff/db user membership community path-params] :as ctx}]
    (let [channel (xt/entity db (parse-uuid (:channel-id path-params)))]
      (if (and (= (:channel/community channel) (:xt/id community)) membership)
        (handler (assoc ctx :channel channel))
        {:status 303
         :headers {"Location" (str "/community/" (:xt/id community))}}))))

(def module
  {:routes ["" {:middleware [mid/wrap-signed-in]}
            ["/app"           {:get app}]
            ["/community"     {:post new-community}]
            ["/community/:id" {:middleware [wrap-community]}
             [""      {:get community}]
             ["/join" {:post join-community}]
             ["/channel" {:post new-channel}]
             ["/channel/:channel-id" {:middleware [wrap-channel]}
              ["" {:get channel-page
                   :post new-message
                   :delete delete-channel}]
              ["/connect" {:get connect}]]]]
   :on-tx on-new-message})
