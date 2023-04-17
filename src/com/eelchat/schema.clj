(ns com.eelchat.schema)

(def schema
  {:user/id :uuid
   :user    [:map {:closed true}
             [:xt/id          :user/id]
             [:user/email     :string]
             [:user/joined-at inst?]]

   :comm/id   :uuid
   :community [:map {:closed true}
               [:xt/id      :comm/id]
               [:comm/title :string]]

   :mem/id     :uuid
   :membership [:map {:closed true}
                [:xt/id     :mem/id]
                [:mem/user  :user/id]
                [:mem/comm  :comm/id]
                [:mem/roles [:set [:enum :admin]]]]

   :chan/id :uuid
   :channel [:map {:closed true}
             [:xt/id      :chan/id]
             [:chan/title :string]
             [:chan/comm  :comm/id]]

   :sub/id       :uuid
   :subscription [:map {:closed true}
                  [:xt/id             :sub/id]
                  [:sub/url           :string]
                  [:sub/chan          :chan/id]
                  [:sub/last-post-uri {:optional true} :string]
                  [:sub/fetched-at    {:optional true} inst?]
                  [:sub/last-modified {:optional true} :string]
                  [:sub/etag          {:optional true} :string]]

   :msg/id  :uuid
   :message [:map {:closed true}
             [:xt/id          :msg/id]
             [:msg/mem        [:or :mem/id [:enum :system]]]
             [:msg/text       :string]
             [:msg/channel    :chan/id]
             [:msg/created-at inst?]]})

(def plugin
  {:schema schema})
