(ns com.eelchat.schema
  (:require [malli.core :as malc]
            [malli.registry :as malr]))

(def schema
  {:user/id :uuid
   :user/email :string
   :user/joined-at inst?
   :user [:map {:closed true}
          [:xt/id :user/id]
          :user/email
          :user/joined-at]})

(def malli-opts {:registry (malr/composite-registry malc/default-registry schema)})
