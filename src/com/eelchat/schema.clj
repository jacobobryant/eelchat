(ns com.eelchat.schema)

(def schema
  {:user/id :uuid
   :user [:map {:closed true}
          [:xt/id          :user/id]
          [:user/email     :string]
          [:user/joined-at inst?]]

   :community/id :uuid
   :community [:map {:closed true}
               [:xt/id           :community/id]
               [:community/title :string]]

   :membership/id :uuid
   :membership [:map {:closed true}
                [:xt/id                :membership/id]
                [:membership/user      :user/id]
                [:membership/community :community/id]
                [:membership/roles     [:set [:enum :admin]]]]

   :channel/id :uuid
   :channel [:map {:closed true}
             [:xt/id             :channel/id]
             [:channel/title     :string]
             [:channel/community :community/id]]

   :message/id :uuid
   :message [:map {:closed true}
             [:xt/id              :message/id]
             [:message/membership :membership/id]
             [:message/text       :string]
             [:message/channel    :channel/id]
             [:message/created-at inst?]]})

(def module
  {:schema schema})
