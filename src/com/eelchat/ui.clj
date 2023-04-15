(ns com.eelchat.ui
  (:require [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.eelchat.settings :as settings]
            [com.eelchat.ui.icons :refer [icon]]
            [com.biffweb :as biff :refer [q]]
            [ring.middleware.anti-forgery :as csrf]))

(defn css-path []
  (if-some [f (io/file (io/resource "public/css/main.css"))]
    (str "/css/main.css?t=" (.lastModified f))
    "/css/main.css"))

(defn base [{:keys [::recaptcha] :as ctx} & body]
  (apply
   biff/base-html
   (-> ctx
       (merge #:base{:title settings/app-name
                     :lang "en-US"
                     :description "The world's finest discussion platform."
                     :image "/img/logo.png"})
       (update :base/head (fn [head]
                            (concat [[:link {:rel "stylesheet" :href (css-path)}]
                                     [:script {:src "https://unpkg.com/htmx.org@1.9.0"}]
                                     [:script {:src "https://unpkg.com/htmx.org/dist/ext/ws.js"}]
                                     [:script {:src "https://unpkg.com/hyperscript.org@0.9.8"}]
                                     [:link {:href "/apple-touch-icon.png", :sizes "180x180", :rel "apple-touch-icon"}]
                                     [:link {:href "/favicon-32x32.png", :sizes "32x32", :type "image/png", :rel "icon"}]
                                     [:link {:href "/favicon-16x16.png", :sizes "16x16", :type "image/png", :rel "icon"}]
                                     [:link {:href "/site.webmanifest", :rel "manifest"}]
                                     [:link {:color "#5bbad5", :href "/safari-pinned-tab.svg", :rel "mask-icon"}]
                                     [:meta {:content "#da532c", :name "msapplication-TileColor"}]
                                     [:meta {:content "#0d9488", :name "theme-color"}]
                                     (when recaptcha
                                       [:script {:src "https://www.google.com/recaptcha/api.js"
                                                 :async "async" :defer "defer"}])]
                                    head))))
   body))

(defn page [ctx & body]
  (base
   ctx
   [:.bg-orange-50.flex.flex-col.flex-grow
    [:.flex-grow]
    [:.p-3.mx-auto.max-w-screen-sm.w-full
     (when (bound? #'csrf/*anti-forgery-token*)
       {:hx-headers (cheshire/generate-string
                     {:x-csrf-token csrf/*anti-forgery-token*})})
     body]
    [:.flex-grow]
    [:.flex-grow]]))

(defn channels [{:keys [biff/db community roles]}]
  (when (some? roles)
    (sort-by
     :chan/title
     (q db
        '{:find (pull channel [*])
          :in [comm]
          :where [[channel :chan/comm comm]]}
        (:xt/id community)))))

(defn app-page [{:keys [biff/db uri user community roles channel] :as ctx} & body]
  (base
   ctx
   [:.flex.bg-orange-50
    {:hx-headers (cheshire/generate-string
                  {:x-csrf-token csrf/*anti-forgery-token*})}
    [:.h-screen.w-80.p-3.pr-0.flex.flex-col.flex-grow
     [:select
      {:class '[text-sm
                cursor-pointer
                focus:border-teal-600
                focus:ring-teal-600]
       :onchange "window.location = this.value"}
      [:option {:value "/app"}
       "Select a community"]
      (for [{:keys [mem/comm]} (:user/mems user)
            :let [url (str "/community/" (:xt/id comm))]]
        [:option.cursor-pointer
         {:value url
          :selected (when (str/starts-with? uri url)
                      true)}
         (:comm/title comm)])]
     [:.h-4]
     (for [chan (channels ctx)
           :let [active (= (:xt/id chan) (:xt/id channel))
                 href (str "/community/" (:xt/id community)
                           "/channel/" (:xt/id chan))]]
       [:.mt-4.flex.justify-between.leading-none
        (if active
          [:span.font-bold (:chan/title chan)]
          [:a.link {:href href}
           (:chan/title chan)])
        (when (contains? roles :admin)
          [:button.opacity-50.hover:opacity-100.flex.items-center
           {:hx-delete href
            :hx-confirm (str "Delete " (:chan/title chan) "?")
            :hx-target "closest div"
            :hx-swap "outerHTML"
            :_ (when active
                 (str "on htmx:afterRequest set window.location to '/community/" (:xt/id community) "'"))}
           (icon :x {:class "w-3 h-3"})])])
     [:.grow]
     (when (contains? roles :admin)
       [:<>
        (biff/form
         {:action (str "/community/" (:xt/id community) "/channel")}
         [:button.btn.w-full {:type "submit"} "New channel"])
        [:.h-3]])
     (biff/form
      {:action "/community"}
      [:button.btn.w-full {:type "submit"} "New community"])
     [:.h-3]
     [:.text-sm (:user/email user) " | "
      (biff/form
       {:action "/auth/signout"
        :class "inline"}
       [:button.text-teal-600.hover:text-teal-800 {:type "submit"}
        "Sign out"])]]
    [:.h-screen.w-full.p-3.flex.flex-col
     body]]))
