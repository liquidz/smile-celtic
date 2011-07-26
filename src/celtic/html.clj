(ns celtic.html
  (:use
    clj-gravatar.core
    [celtic smilevideo model]
    [hiccup.core :only [html]]
    )
  (:require
    [appengine-magic.services.user :as du]))

(def *title* "ｹﾙﾄｶﾞｷｹﾙﾄｷｲﾃ")

(defn header []
  [:header
   [:div {:id "top"}
    [:h1 {:class "left"} [:a {:href "/"} *title*]]
    [:p {:class "right"} (if (du/user-logged-in?)
          (list [:span (.getEmail (du/current-user))] " / " [:a {:href (du/logout-url)} "ログアウト"])
          [:a {:href (du/login-url)} "ログイン"])]]
   (if (du/user-logged-in?)
     [:nav
      [:ul
       [:li [:a {:href "/likes"} "like musics"]]
       [:li [:a {:href "/dislikes"} "dislike musics"]]]])
   ])

(defn footer []
  [:footer
   [:img {:src "http://code.google.com/appengine/images/appengine-noborder-120x30.gif" :alt "Powered by Google App Engine"}]
   [:img {:src "/img/clojure-logo.png" :alt "Powered by Clojure"}]
   [:p [:a {:href "https://github.com/liquidz/smile-celtic"} "ソースコード"]
    "&nbsp;|&nbsp;Copyright &copy; 2011 " [:a {:href "http://twitter.com/uochan"} "@uochan"] "."]])

(defn layout [& body]
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:link {:rel "stylesheet" :type "text/css" :href "/css/main.css"}]
    [:script {:type "text/javascript" :src "/js/jquery-1.6.1.min.js"}]
    [:script {:type "text/javascript" :src "/js/main.js"}]
    [:title *title*]]
   [:body body]])

(defn to-html5 [v]
  (str "<!DOCTYPE html>" (html v)))

(defn video [feed]
  (let [script (-> feed :key key->script)]
    [:div {:class "movie"}
     script
     (if (du/user-logged-in?)
       [:p
        (cond
          (:like feed) (list "LIKE" [:button {:class "cancel" :data-key (:key feed) :data-type "like"} "cancel"])
          (:dislike feed) (list "DISLIKE" [:button {:class "cancel" :data-key (:key feed) :data-type "dislike"} "cancel"])
          :else (list
                  [:button {:class "set" :data-key (:key feed) :data-type "like"} "like"]
                  [:button {:class "set" :data-key (:key feed) :data-type "dislike"} "dislike"]))])]))

(defn- make-html-with-feeds
  "トップページを作成"
  [feeds page fpp]
  (to-html5
    (layout
      (header)
      [:div {:id "screen"}
       (if (> page 1)
         [:a {:id "prev" :href (str "/?page=" (dec page) "&fpp=" fpp)} "&laquo;"])
       (map video feeds)
       [:a {:id "next" :href (str "/?page=" (inc page) "&fpp=" fpp)} "&raquo;"]]
      (footer))))

(defn make-html
  "トップページを作成"
  [page fpp]
  (let [all-feeds (load-search-feed)
        feeds (take fpp (drop (* (dec page) fpp) all-feeds))]
    (make-html-with-feeds feeds page fpp)))

(defn make-likes-html
  [page fpp]
  (let [like-keys (map #(assoc % :like true :dislike false) (get-likes :limit fpp :page page))]
    (make-html-with-feeds like-keys page fpp)))

(defn make-dislikes-html
  [page fpp]
  (let [dislike-keys (map #(assoc % :like false :dislike true) (get-dislikes :limit fpp :page page))]
    (make-html-with-feeds dislike-keys page fpp)))
