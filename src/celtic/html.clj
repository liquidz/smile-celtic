(ns celtic.html
  (:use
    clj-gravatar.core
    [celtic smilevideo model]
    [hiccup.core :only [html]]
    )
  (:require
    [appengine-magic.services.user :as du]
    [clojure.contrib.generic.math-functions :as math]))

(def TITLE "Smile Celtic Music")

(defn menu [basedir logged-in?]
  (let [ls [["/" "トップ" "home" false]
            ["/likes" "好きな曲" "like_music" true]
            ["/dislikes" "嫌いな曲" "dislike_music" true]
            ["/shuffle" "シャッフル" "shuffle" false]]]
    [:nav
     [:ul
      (map (fn [[uri label id need-auth?]]
             (let [li [:li [:a {:href uri :id id :class (if (= basedir uri) "now" "_")} label]]]
               (if need-auth? (if logged-in? li) li))
             ) ls)]]))

(defn header [& {:keys [uri basedir] :or [uri "/", basedir "/"]}]
  [:header
   [:div {:id "top"}
    [:h1 {:class "left"} [:a {:href "/"} TITLE]]
    [:div {:class "right"}
     [:p (if (du/user-logged-in?)
           (list [:span (.getEmail (du/current-user))] " / " [:a {:href (du/logout-url :destination uri)} "ログアウト"])
           [:a {:href (du/login-url :destination uri)} "ログイン"])]]]
   (menu basedir (du/user-logged-in?))])

(defn footer []
  [:footer
   [:img {:src "http://code.google.com/appengine/images/appengine-silver-120x30.gif" :alt "Powered by Google App Engine"}]
   " &nbsp; "
   [:img {:src "/img/clojure-logo.png" :alt "Powered by Clojure"}]
   [:p [:a {:href "https://github.com/liquidz/smile-celtic"} "ソースコード"]
    "&nbsp;|&nbsp;Copyright &copy; 2011 " [:a {:href "http://twitter.com/uochan"} "@uochan"] "."]])

(defn- base-layout [head body]
  [:html
   [:head [:meta {:charset "utf-8"}] head]
   [:body body]])

(defn layout [& body]
  (base-layout
    (list [:link {:rel "stylesheet" :type "text/css" :href "/css/main.css"}]
          [:script {:type "text/javascript" :src "/js/jquery-1.6.1.min.js"}]
          [:script {:type "text/javascript" :src "/js/main.js"}]
          [:title TITLE])
    body))

(defn simple-layout [body]
  (base-layout [:title TITLE] body))

(defn to-html5 [v]
  (str "<!DOCTYPE html>" (html v)))

(defn- hidden-pattern [feed]
  (let [none "display: none;"]
    (cond
      (:like feed) {:cancel-like "" :cancel-dislike none :set none}
      (:dislike feed) {:cancel-like none :cancel-dislike "" :set none}
      :else {:cancel-like none :cancel-dislike none :set ""})))

(defn video [feed]
  (let [script (-> feed :key key->script-tag)
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

(defn- page-uri [page fpp basedir rsspage]
  (str basedir "?page=" page "&fpp=" fpp "&rsspage=" rsspage))

(defn- make-page-link [text page fpp basedir rsspage & {:keys [id class]}]
  (let [base {:href (page-uri page fpp basedir rsspage)}
        base-with-id (if id (assoc base :id id) base)
        attr (if class (assoc base-with-id :class class) base-with-id)]
    [:a attr text]))

(defn- pager [page fpp total basedir feed-count rsspage]
  (let [max-page (int (math/ceil (/ total fpp)))]
    [:ul {:id "pager"}
     (if (> page 1)
       (make-page-link "&laquo;" (dec page) fpp basedir rsspage :id "prev"))
     (map #(vector :li (make-page-link % % fpp basedir rsspage :id (if (= % page) "now"))) (range 1 (inc max-page)))
     (if (and (= feed-count fpp) (not= total (* page fpp)))
       (make-page-link "&raquo;" (inc page) fpp basedir rsspage :id "next"))]))

(defn- rsspager [rsspage fpp]
  (let [page-max (get-rsspage-max)]
    [:div {:id "rsspager"} [:span "検索フィード 移動&raquo;"]
     [:ul (map #(vector :li (make-page-link % 1 fpp "/" %
                                            :id (if (= % rsspage) "nowrss")
                                            )) (range 1 (inc page-max)))]]))

(defn- make-html-with-feeds
  "トップページを作成"
  [feeds page fpp & {:keys [basedir total rsspage show-rsspager?]
                     :or {basedir "/", rsspage 1, show-rsspager? false}}]
  (let [feed-count (count feeds)
        uri (page-uri page fpp basedir rsspage)]
    (to-html5
      (layout
        (header :uri uri :basedir basedir)
        [:div {:id "screen"} (map video feeds)]
        (if total (pager page fpp total basedir feed-count rsspage))
        (if show-rsspager? (rsspager rsspage fpp))
        (footer)))))

(defn make-html
  "トップページを作成"
  [& {:keys [page fpp rsspage]}]
  (let [all-feeds (load-search-feed :page rsspage)
        feeds (take fpp (drop (* (dec page) fpp) all-feeds))]
    (make-html-with-feeds feeds page fpp :total (count all-feeds)
                          :rsspage rsspage :show-rsspager? true)))

(defn make-likes-html
  [& {:keys [page fpp]}]
  (let [like-keys (map #(assoc % :like true :dislike false) (get-likes :limit fpp :page page))]
    (make-html-with-feeds like-keys page fpp
                          :basedir "/likes" :total (count-likes))))

(defn make-dislikes-html
  [& {:keys [page fpp]}]
  (let [dislike-keys (map #(assoc % :like false :dislike true) (get-dislikes :limit fpp :page page))]
    (make-html-with-feeds dislike-keys page fpp
                          :basedir "/dislikes" :total (count-dislikes))))

(defn make-shuffle-html
  [& {:keys [fpp]}]
  (let [all-feeds (load-search-feed-randomly)
        feeds (map (fn [_] (rand-nth all-feeds)) (range fpp))]
    (make-html-with-feeds feeds 0 fpp :basedir "/shuffle")))

(defn make-movie-html [key]
  (to-html5
    (simple-layout [:script {:type "text/javascript" :src (str "/script/" key)}]))
  )
