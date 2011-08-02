(ns celtic.smilevideo
  (:use celtic.model)
  (:require
    [appengine-magic.services.memcache :as mem]
    [clojure.contrib.string :as string]
    [clojure.contrib.io :as io]
    [clojure.contrib.zip-filter.xml :as zfx]
    [clojure.contrib.generic.math-functions :as math]))

; ケルトが聴ケルト聞いての検索フィード
(def SEARCH_URI "http://www.nicovideo.jp/tag/%E3%82%B1%E3%83%AB%E3%83%88%E3%81%8C%E8%81%B4%E3%82%B1%E3%83%AB%E3%83%88%E8%81%9E%E3%81%84%E3%81%A6?rss=2.0")
(def TOTAL_FEEDS_PER_PAGE 32)

(defn- make-search-uri [sort page]
  (str SEARCH_URI
       (if (string/blank? sort) "" (str "&sort=" sort))
       "&page=" page))

(defn load-xml
  "uriからXMLをzxmlとして取得。ニコ動への負荷を考慮してcacheも保持"
  [uri & {:keys [update?] :or {update? false}}]
  (if (and (mem/contains? uri) (not update?))
    (mem/get uri)
    (try
      (let [res (clojure.zip/xml-zip (clojure.xml/parse uri))]
        (mem/put! uri res)
        res)
      (catch Exception e nil))))

;; feed操作系
(defn- get-feed-total-item-count
  "ニコ動検索結果の総数を取得"
  [zxml]
  (let [desc (first (zfx/xml-> zxml :channel :description zfx/text))]
    (Integer/parseInt (last (re-seq #"\d+" desc)))))
(defn get-feed-items [zxml]
  (zfx/xml-> zxml :channel :item))
(defn _get-feed-data [key zxml] (first (zfx/xml-> zxml key zfx/text)))
(def get-feed-title (partial _get-feed-data :title))
(def get-feed-link (partial _get-feed-data :link))
(def get-feed-date (partial _get-feed-data :date))
(def get-feed-description (partial _get-feed-data :description))

(defn embeddable?
  "ニコ動情報取得APIから外部プレイヤで再生可能かどうかを取得し返す"
  [key & {:keys [default] :or {default true}}]
  (if (embeddable-exists? key)
    (get-embeddable key)
    (if-let [zxml (load-xml (str "http://ext.nicovideo.jp/api/getthumbinfo/" key))]
      (set-embeddable key (= "1" (first (zfx/xml-> zxml :thumb :embeddable zfx/text))))
      default)))

(defn get-key
  "ニコ動の動画キー(smXXXXのようなもの)をリンクから取得"
  [link] (last (string/split #"/" link)))

(defn item->map
  "検索フィードのzxmlをマップに変換"
  [item]
  (let [link (get-feed-link item)
        key (get-key link)]
    {:title (get-feed-title item)
     :link link
     :date (get-feed-date item)
     :description (get-feed-description item)
     :key key
     :embeddable (embeddable? key)
     :like-or-dislike? (or (like? key) (dislike? key))
     ;:like (like? key)
     ;:dislike (dislike? key)
     }))

(defn get-latest-total-item-count []
  (get-feed-total-item-count (load-xml SEARCH_URI)))

(defn update-total-item-count []
  (set-total (get-latest-total-item-count)))

(defn get-total-item-count []
  (if (total-exists?)
    (get-total)
    (update-total-item-count)))

(defn get-rsspage-max []
  (int (math/ceil (/ (get-total-item-count) TOTAL_FEEDS_PER_PAGE))))

(defn load-search-feed [& {:keys [sort page update?] :or {sort "", page 1, update? false}}]
  (let [zxml (load-xml (make-search-uri sort page) :update? update?)]
    (filter :embeddable (remove :like-or-dislike? (map item->map (get-feed-items zxml))))))

(defn load-search-feed-randomly [& {:keys [sort] :or {sort ""}}]
  (let [page (inc (rand-int (get-rsspage-max)))]
    (load-search-feed :sort sort :page page)))

(defn make-extscript-uri [key & {:keys [width height] :or {width 320 height 265}}]
  (str "http://ext.nicovideo.jp/thumb_watch/" key "?w=" width "&h=" height))

(defn key->script-tag
  "動画キーから外部プレイヤ呼び出しのscriptタグを作成"
  [key & {:keys [width height] :or {width 320 height 265}}]
  [:script {:type "text/javascript" :src (str "http://ext.nicovideo.jp/thumb_watch/" key "?w=" width "&h=" height)}])

(defn extscript-with-autoplay [uri]
  (apply str (map #(if (not= -1 (.indexOf % "thumbPlayKey"))
                     (str % ", fv_autoplay: '1'") %) (io/read-lines uri))))


