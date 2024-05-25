(ns com.eelchat.ui
  (:require [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.eelchat.settings :as settings]
            [com.eelchat.ui.icons :refer [icon]]
            [com.biffweb :as biff :refer [q]]
            [ring.middleware.anti-forgery :as csrf]
            [ring.util.response :as ring-response]
            [rum.core :as rum]))

(defn css-path []
  (if-some [last-modified (some-> (io/resource "public/css/main.css")
                                  ring-response/resource-data
                                  :last-modified
                                  (.getTime))]
    (str "/css/main.css?t=" last-modified)
    "/css/main.css"))

(defn js-path []
  (if-some [last-modified (some-> (io/resource "public/js/main.js")
                                  ring-response/resource-data
                                  :last-modified
                                  (.getTime))]
    (str "/js/main.js?t=" last-modified)
    "/js/main.js"))

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
                                     [:script {:src (js-path)}]
                                     [:script {:src "https://unpkg.com/htmx.org@1.9.10"}]
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

(defn on-error [{:keys [status ex] :as ctx}]
  {:status status
   :headers {"content-type" "text/html"}
   :body (rum/render-static-markup
          (page
           ctx
           [:h1.text-lg.font-bold
            (if (= status 404)
              "Page not found."
              "Something went wrong.")]))})

(defn channels [{:keys [biff/db community roles]}]
  (when (some? roles)
    (sort-by
     :channel/title
     (q db
        '{:find (pull channel [*])
          :in [community]
          :where [[channel :channel/community community]]}
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
      (for [{:keys [membership/community]} (:user/memberships user)
            :let [url (str "/community/" (:xt/id community))]]
        [:option.cursor-pointer
         {:value url
          :selected (str/starts-with? uri url)}
         (:community/title community)])]
     [:.h-4]
     (for [c (channels ctx)
           :let [active (= (:xt/id c) (:xt/id channel))

                 href (str "/community/" (:xt/id community)

                           "/channel/" (:xt/id c))]]
       [:.mt-4.flex.justify-between.leading-none
        (if active
          [:span.font-bold (:channel/title c)]
          [:a.link {:href href}
           (:channel/title c)])
        (when (contains? roles :admin)
          [:button.opacity-50.hover:opacity-100.flex.items-center
           {:hx-delete href
            :hx-confirm (str "Delete " (:channel/title c) "?")
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
