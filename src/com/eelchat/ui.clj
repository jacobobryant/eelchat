(ns com.eelchat.ui
  (:require [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.eelchat.ui.icons :refer [icon]]
            [com.biffweb :as biff :refer [q]]
            [ring.middleware.anti-forgery :as anti-forgery]))

(defn css-path []
  (if-some [f (io/file (io/resource "public/css/main.css"))]
    (str "/css/main.css?t=" (.lastModified f))
    "/css/main.css"))

(defn base [opts & body]
  (apply
   biff/base-html
   (-> opts
       (merge #:base{:title "eelchat"
                     :lang "en-US"
                     :description "The world's finest discussion platform."
                     :image "/img/logo.png"})
       (update :base/head (fn [head]
                            (concat [[:link {:rel "stylesheet" :href (css-path)}]
                                     [:script {:src "https://unpkg.com/htmx.org@1.6.1"}]
                                     [:script {:src "https://unpkg.com/hyperscript.org@0.9.3"}]
                                     [:link {:href "/apple-touch-icon.png", :sizes "180x180", :rel "apple-touch-icon"}]
                                     [:link {:href "/favicon-32x32.png", :sizes "32x32", :type "image/png", :rel "icon"}]
                                     [:link {:href "/favicon-16x16.png", :sizes "16x16", :type "image/png", :rel "icon"}]
                                     [:link {:href "/site.webmanifest", :rel "manifest"}]
                                     [:link {:color "#5bbad5", :href "/safari-pinned-tab.svg", :rel "mask-icon"}]
                                     [:meta {:content "#da532c", :name "msapplication-TileColor"}]
                                     [:meta {:content "#0d9488", :name "theme-color"}]]
                                    head))))
   body))

(defn page [opts & body]
  (base
   opts
   [:.bg-orange-50.flex.flex-col.flex-grow
    [:.grow]
    [:.p-3.mx-auto.max-w-screen-sm.w-full
     body]
    [:div {:class "grow-[2]"}]]))

(defn channels [{:keys [biff/db community roles]}]
  (when (some? roles)
    (sort-by
     :chan/title
     (q db
        '{:find (pull channel [*])
          :in [comm]
          :where [[channel :chan/comm comm]]}
        (:xt/id community)))))

(defn app-page [{:keys [biff/db uri user community roles channel] :as opts} & body]
  (base
   opts
   [:.flex.bg-orange-50
    {:hx-headers (cheshire/generate-string
                  {:x-csrf-token anti-forgery/*anti-forgery-token*})}
    [:.h-screen.w-80.p-3.pr-0.flex.flex-col.flex-grow
     [:select
      {:class '[text-sm
                cursor-pointer
                focus:border-teal-600
                focus:ring-teal-600]
       :onchange "window.location = this.value"}
      [:option {:value "/app"
                :selected (when (= uri "/app"))}
       "Select a community"]
      (for [{:keys [mem/comm]} (:user/mems user)
            :let [url (str "/community/" (:xt/id comm))]]
        [:option.cursor-pointer
         {:value url
          :selected (when (str/starts-with? uri url)
                      true)}
         (:comm/title comm)])]
     [:.h-4]
     (for [chan (channels opts)
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
