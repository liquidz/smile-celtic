(ns celtic.smilevideo
  (:use celtic.model)
  (:require
    [appengine-magic.services.memcache :as mem]
    [clojure.contrib.string :as string]
    [clojure.contrib.zip-filter.xml :as zfx]
    [clojure.contrib.generic.math-functions :as math]))

; ケルトが聴ケルト聞いての検索フィード
(def search-uri "http://www.nicovideo.jp/tag/%E3%82%B1%E3%83%AB%E3%83%88%E3%81%8C%E8%81%B4%E3%82%B1%E3%83%AB%E3%83%88%E8%81%9E%E3%81%84%E3%81%A6?rss=2.0")
(def total-item-key "total-item")
(def total-feeds-per-page 32)

(defn- make-search-uri
  [sort page]
  (str search-uri
       (if (string/blank? sort) "" (str "&sort=" sort))
       "&page=" page))

(defn load-xml
  "uriからXMLをzxmlとして取得。ニコ動への負荷を考慮してcacheも保持"
  [uri]
  (if (mem/contains? uri)
    (mem/get uri)
    (try
      (let [res (clojure.zip/xml-zip (clojure.xml/parse uri))]
        (mem/put! uri res)
        res)
      (catch Exception e nil))))

;; feed操作系
(defn- get-total-item-count
  "ニコ動検索結果の総数を取得"
  [zxml]
  (let [desc (first (zfx/xml-> zxml :channel :description zfx/text))]
    (Integer/parseInt (last (re-seq #"\d+" desc)))))
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
    (if (nil? zxml)
      default
      (= "1" (first (zfx/xml-> zxml :thumb :embeddable zfx/text))))))

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
     :embeddable (embeddable? key)
     :like (like? key)
     :dislike (dislike? key)}))

(defn- update-and-get-total-item-count [& {:keys [zxml uri get?] :or {get? false}}]
  (if (mem/contains? total-item-key)
    (if get? (mem/get total-item-key))
    (let [_zxml (if zxml zxml (if uri (load-xml uri)))
          total (get-total-item-count _zxml)]
      (mem/put! total-item-key total)
      (if get? total))))

(defn load-total-item-count []
  (update-and-get-total-item-count :uri search-uri :get? true))

(defn load-rsspage-max []
  (int (math/ceil (/ (load-total-item-count) total-feeds-per-page))))

(defn load-search-feed
  "検索フィードから情報を取得"
  [& {:keys [sort page random-page?] :or {sort "", page "1", random-page? false}}]
  (if random-page?
    (load-search-feed :sort sort :page (inc (rand-int (load-rsspage-max))))
    (let [zxml (load-xml (make-search-uri sort page))]
      (update-and-get-total-item-count :zxml zxml)
      (filter :embeddable (remove :dislike (map item->map (get-items zxml)))))))

(defn key->script
  "動画キーから外部プレイヤ呼び出しのscriptタグを作成"
  [key & {:keys [width height] :or {width 320 height 265}}]
  [:script {:type "text/javascript" :src (str "http://ext.nicovideo.jp/thumb_watch/" key "?w=" width "&h=" height)}])

