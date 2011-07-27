(ns celtic.html
  (:use
    clj-gravatar.core
    [celtic smilevideo model]
    [hiccup.core :only [html]]
    )
  (:require
    [appengine-magic.services.user :as du]
    [clojure.contrib.generic.math-functions :as math]))

(def *title* "Smile Celtic Music")

(defn menu [basedir]
  (let [ls [["/" "トップ" "home"]
            ["/likes" "好きな曲" "like_music"]
            ["/dislikes" "嫌いな曲" "dislike_music"]]]
    [:nav
     [:ul
      (map (fn [[uri label id]]
             [:li [:a {:href uri :id id :class (if (= basedir uri) "now" "_")} label]]
             ) ls)]]))

(defn header [& {:keys [uri basedir] :or [uri "/", basedir "/"]}]
  [:header
   [:div {:id "top"}
    [:h1 {:class "left"} [:a {:href "/"} *title*]]
    [:div {:class "right"}
     [:p (if (du/user-logged-in?)
           (list [:span (.getEmail (du/current-user))] " / " [:a {:href (du/logout-url :destination uri)} "ログアウト"])
           [:a {:href (du/login-url :destination uri)} "ログイン"])]]]
    (if (du/user-logged-in?) (menu basedir))])

(defn footer []
  [:footer
   [:img {:src "http://code.google.com/appengine/images/appengine-silver-120x30.gif" :alt "Powered by Google App Engine"}]
   " &nbsp; "
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

(defn- hidden-pattern [feed]
  (let [none "display: none;"]
    (cond
      (:like feed) {:cancel-like "" :cancel-dislike none :set none}
      (:dislike feed) {:cancel-like none :cancel-dislike "" :set none}
      :else {:cancel-like none :cancel-dislike none :set ""})))

(defn video [feed]
  (let [script (-> feed :key key->script)
        hidden (hidden-pattern feed)]
    [:div {:class "movie"}
     script
     (if (du/user-logged-in?)
       [:p
        [:img {:style (:cancel-like hidden) :src "/img/like.png" :class "cancel" :data-key (:key feed) :data-type "like"}]
        [:img {:style (:cancel-dislike hidden) :src "/img/dislike.png" :class "cancel" :data-key (:key feed) :data-type "dislike"}]
        [:span {:style (:set hidden) :data-key (:key feed)}
         [:img {:src "/img/like.png" :class "set" :data-key (:key feed) :data-type "like" :alt "like"}]
         " &nbsp; "
         [:img {:src "/img/dislike.png" :class "set" :data-key (:key feed) :data-type "dislike" :alt "dislike"}]]])]))

(defn- page-uri [page fpp basedir]
  (str basedir "?page=" page "&fpp=" fpp))

(defn- make-page-link [text page fpp basedir & {:keys [id class]}]
  (let [base {:href (page-uri page fpp basedir)}
        base-with-id (if id (assoc base :id id) base)
        attr (if class (assoc base-with-id :class class) base-with-id)]
    [:a attr text]))

(defn- pager [page fpp total basedir feed-count]
  (let [max-page (int (math/ceil (/ total fpp)))]
    [:ul {:id "pager"}
     (if (> page 1)
       (make-page-link "&laquo;" (dec page) fpp basedir :id "prev"))
     (map #(vector :li (make-page-link % % fpp basedir :id (if (= % page) "now"))) (range 1 (inc max-page)))
     (if (and (= feed-count fpp) (not= total (* page fpp)))
       (make-page-link "&raquo;" (inc page) fpp basedir :id "next"))]))

(defn- make-html-with-feeds
  "トップページを作成"
  [feeds page fpp & {:keys [basedir total] :or {basedir "/"}}]
  (let [feed-count (count feeds)
        uri (page-uri page fpp basedir)]
    (to-html5
      (layout
        (header :uri uri :basedir basedir)
        [:div {:id "screen"} (map video feeds)]
        (if total
          [:div {:id "pager"} (pager page fpp total basedir feed-count)])
        (footer)))))

(defn make-html
  "トップページを作成"
  [page fpp]
  (let [all-feeds (load-search-feed)
        feeds (take fpp (drop (* (dec page) fpp) all-feeds))]
    (make-html-with-feeds feeds page fpp :total (count all-feeds))))

(defn make-likes-html
  [page fpp]
  (let [like-keys (map #(assoc % :like true :dislike false) (get-likes :limit fpp :page page))]
    (make-html-with-feeds like-keys page fpp
                          :basedir "/likes" :total (count-likes))))

(defn make-dislikes-html
  [page fpp]
  (let [dislike-keys (map #(assoc % :like false :dislike true) (get-dislikes :limit fpp :page page))]
    (make-html-with-feeds dislike-keys page fpp
                          :basedir "/dislikes" :total (count-dislikes))))
