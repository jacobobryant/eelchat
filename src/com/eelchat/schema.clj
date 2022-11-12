(ns com.eelchat.schema
  (:require [malli.core :as malc]
            [malli.registry :as malr]))

(def schema
  {:user/id :uuid
   :user    [:map {:closed true}
             [:xt/id          :user/id]
             [:user/email     :string]
             [:user/joined-at inst?]
             [:user/handle    {:optional true} :string]]

   :comm/id   :uuid
   :community [:map {:closed true}
               [:xt/id       :comm/id]
               [:comm/title  :string]]

   :mem/id     :uuid
   :membership [:map {:closed true}
                [:xt/id     :mem/id]
                [:mem/user  :user/id]
                [:mem/comm  :comm/id]
                [:mem/roles [:set [:enum :admin :trusted]]]]

   :chan/id :uuid
   :channel [:map {:closed true}
             [:xt/id       :chan/id]
             [:chan/title  :string]
             [:chan/comm   :comm/id]
             [:chan/type   [:enum :chat :forum :threaded]]
             [:chan/access [:enum :public :private]]]

   :msg/id  :uuid
   :message [:map {:closed true}
             [:xt/id          :msg/id]
             [:msg/mem        :mem/id]
             [:msg/text       :string]
             [:msg/channel    :chan/id]
             [:msg/created-at inst?]
             [:msg/title      {:optional true} :string]
             [:msg/parent     {:optional true} :msg/id]]})

(def malli-opts {:registry (malr/composite-registry malc/default-registry schema)})
