(ns celtic.core
  (:use
    [compojure.core :only [GET POST defroutes wrap!]]
    [compojure.route :only [not-found]]
    [ring.util.response :only [redirect]]
    [ring.middleware.params :only [wrap-params]]
    [ring.middleware.keyword-params :only [wrap-keyword-params]]
    [ring.middleware.session :only [wrap-session]]
    [hiccup.core :only [html]])
  (:require
    [appengine-magic.core :as ae]
    [appengine-magic.services.memcache :as mem]
    [appengine-magic.services.datastore :as ds]
    [appengine-magic.services.user :as du]
    [clojure.contrib.string :as string]
    [clojure.contrib.io :as io]
    [clojure.contrib.zip-filter.xml :as zfx]
    [clojure.contrib.json :as json]
    ))

; ケルトが聴ケルト聞いての検索フィード
(def search-uri "http://www.nicovideo.jp/tag/%E3%82%B1%E3%83%AB%E3%83%88%E3%81%8C%E8%81%B4%E3%82%B1%E3%83%AB%E3%83%88%E8%81%9E%E3%81%84%E3%81%A6?sort=f&rss=2.0")

(defn- toi [s] (Integer/parseInt s))

(defn load-xml
  "uriからXMLをzxmlとして取得。ニコ動への負荷を考慮してcacheも保持"
  [uri]
  (if (mem/contains? uri)
    (mem/get uri)
    (let [res (clojure.zip/xml-zip (clojure.xml/parse uri))]
      (mem/put! uri res)
      res)))

;; feed操作系
(defn get-items [zxml]
  (zfx/xml-> zxml :channel :item))
(defn _get-data [key zxml] (first (zfx/xml-> zxml key zfx/text)))
(def get-title (partial _get-data :title))
(def get-link (partial _get-data :link))
(def get-date (partial _get-data :date))
(def get-description (partial _get-data :description))


(defn embeddable?
  "ニコ動情報取得APIから外部プレイヤで再生可能かどうかを取得し返す"
  [key & {:keys [default] :or {default true}}]
  (let [zxml (load-xml (str "http://ext.nicovideo.jp/api/getthumbinfo/" key))]
    (try (= "1" (first (zfx/xml-> zxml :thumb :embeddable zfx/text)))
      (catch Exception e default))))

(defn get-key
  "ニコ動の動画キー(smXXXXのようなもの)をリンクから取得"
  [link] (last (string/split #"/" link)))

(defn item->map
  "検索フィードのzxmlをマップに変換"
  [item]
  (let [link (get-link item)
        key (get-key link)]
    {:title (get-title item)
     :link link
     :date (get-date item)
     :description (get-description item)
     :key key
     :embeddable (embeddable? key)}))

(defn load-search-feed
  "検索フィードから情報を取得"
  []
  (filter :embeddable (map item->map (get-items (load-xml search-uri)))))

(defn key->script
  "動画キーから外部プレイヤ呼び出しのscriptタグを作成"
  [key & {:keys [width height] :or {width 320 height 265}}]
  (str "<script type='text/javascript' src='http://ext.nicovideo.jp/thumb_watch/" key "?w=" width "&h=" height "'></script>"))

(defn show-html
  "トップページを作成"
  [page fpp]
  (let [all-feeds (load-search-feed)
        feeds (take fpp (drop (* (dec page) fpp) all-feeds))]
    (str
      "<!DOCTYPE html>"
      (html
        [:html
         [:head
          [:meta {:charset "utf-8"}]
          [:link {:rel "stylesheet" :type "text/css" :href "/css/main.css"}]
          [:title "ｹﾙﾄｶﾞｷｹﾙﾄｷｲﾃ"]]
         [:body
          [:header [:h1 "ｹﾙﾄｶﾞｷｹﾙﾄｷｲﾃ"]]
          [:div {:id "screen"}
           (if (> page 1)
             [:a {:id "prev" :href (str "/?page=" (dec page) "&fpp=" fpp)} "&laquo;"])
           (map (comp key->script :key) feeds)
           [:a {:id "next" :href (str "/?page=" (inc page) "&fpp=" fpp)} "&raquo;"]]

          [:footer
           [:img {:src "http://code.google.com/appengine/images/appengine-noborder-120x30.gif" :alt "Powered by Google App Engine"}]
           [:img {:src "/img/clojure-logo.png" :alt "Powered by Clojure"}]
           [:p [:a {:href "https://github.com/liquidz/smile-celtic"} "ソースコード"]
            "&nbsp;|&nbsp;Copyright &copy; 2011 " [:a {:href "http://twitter.com/uochan"} "@uochan"] "."]]]]))))


(defroutes app-handler
  ; fpp: feed per page
  (GET "/" {{:keys [page fpp] :or {page "1", fpp "3"}} :params}
    (show-html (toi page) (toi fpp)))
  (not-found "page not found"))

(wrap! app-handler wrap-session wrap-keyword-params wrap-params)
(ae/def-appengine-app celtic-app #'app-handler)

