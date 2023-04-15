(ns com.eelchat.ui
  (:require [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [com.eelchat.settings :as settings]
            [com.biffweb :as biff]
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
