(ns com.eelchat.middleware
  (:require [xtdb.api :as xt]))

(defn wrap-redirect-signed-in [handler]
  (fn [{:keys [session] :as ctx}]
    (if (some? (:uid session))
      {:status 303
       :headers {"location" "/app"}}
      (handler ctx))))

(defn wrap-signed-in [handler]
  (fn [{:keys [biff/db session] :as ctx}]
    (if-some [user (xt/pull db
                            '[* {(:mem/_user {:as :user/mems})
                                 [* {:mem/comm [*]}]}]
                            (:uid session))]
      (handler (assoc ctx :user user))
      {:status 303
       :headers {"location" "/?error=not-signed-in"}})))
