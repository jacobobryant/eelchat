(ns com.eelchat.feat.home
  (:require [com.biffweb :as biff]
            [com.eelchat.middleware :as mid]
            [com.eelchat.ui :as ui]
            [com.eelchat.util :as util]))

(defn recaptcha-disclosure [{:keys [link-class]}]
  [:span "This site is protected by reCAPTCHA and the Google "
   [:a {:href "https://policies.google.com/privacy"
        :target "_blank"
        :class link-class}
    "Privacy Policy"] " and "
   [:a {:href "https://policies.google.com/terms"
        :target "_blank"
        :class link-class}
    "Terms of Service"] " apply."])

(defn signin-form [{:keys [recaptcha/site-key] :as sys}]
  (biff/form
   {:id "signin-form"
    :action "/auth/send"}
   [:div [:label {:for "email"} "Email address:"]]
   [:.h-1]
   [:.flex
    [:input#email
     {:name "email"
      :type "email"
      :autocomplete "email"
      :placeholder "Enter your email address"}]
    [:.w-3]
    [:button.btn.g-recaptcha
     (merge
      (when (util/email-signin-enabled? sys)
        {:data-sitekey site-key
         :data-callback "onSubscribe"
         :data-action "subscribe"})
      {:type "submit"})
     "Sign in"]]
   [:.h-1]
   (if (util/email-signin-enabled? sys)
     [:.text-sm (recaptcha-disclosure {:link-class "link"})]
     [:.text-sm
      "Doesn't need to be a real address. "
      "Until you add API keys for Postmark and reCAPTCHA, we'll just print your sign-in "
      "link to the console. See config.edn."])))

(def recaptcha-scripts
  [[:script {:src "https://www.google.com/recaptcha/api.js"
             :async "async"
             :defer "defer"}]
   [:script (biff/unsafe
             (str "function onSubscribe(token) { document.getElementById('signin-form').submit(); }"))]])

(defn home [sys]
  (ui/page
   {:base/head (when (util/email-signin-enabled? sys)
                 recaptcha-scripts)}
   (signin-form sys)))

(def features
  {:routes ["" {:middleware [mid/wrap-redirect-signed-in]}
            ["/" {:get home}]]})
