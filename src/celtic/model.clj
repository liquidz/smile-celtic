(ns celtic.model
  (:use digest.core)
  (:require
    [appengine-magic.services.datastore :as ds]
    [appengine-magic.services.user :as du]
    [clojure.contrib.string :as string]))

(def *limit* 10)

; entity
(ds/defentity Like [^:key id email key])
(ds/defentity Dislike [^:key id email key])

(defn- make-id
  "Like, Dislikeのキーとなるidを作成"
  [email key]
  (sha1str (str email key)))

(defn- get-email []
  (if (du/user-logged-in?)
    (.getEmail (du/current-user))))


;; Like
(defn like [key]
  (if-let [email (get-email)]
    (ds/save! (Like. (make-id email key) email key))))

(defn like? [key]
  (if-let [email (get-email)]
    (ds/exists? Like (make-id email key))))

(defn get-likes [& {:keys [limit page] :or {limit *limit*, page 1}}]
  (if-let [email (get-email)]
    (ds/query :kind Like :filter (= :email email) :limit limit :offset (* limit (dec page)))))

(defn count-likes []
  (ds/query :kind Like :count-only? true))

(defn delete-like [key]
  (if-let [email (get-email)]
    (if-let [target (ds/retrieve Like (make-id email key))]
      (ds/delete! target))))

;; Dislike
(defn dislike [key]
  (if-let [email (get-email)]
    (ds/save! (Dislike. (make-id email key) email key))))

(defn dislike? [key]
  (if-let [email (get-email)]
    (ds/exists? Dislike (make-id email key))))

(defn count-dislikes []
  (ds/query :kind Dislike :count-only? true))

(defn get-dislikes [& {:keys [limit page] :or {limit *limit*, page 1}}]
  (if-let [email (get-email)]
    (ds/query :kind Dislike :filter (= :email email) :limit limit :offset (* limit (dec page)))))

(defn delete-dislike [key]
  (if-let [email (get-email)]
    (if-let [target (ds/retrieve Dislike (make-id email key))]
      (ds/delete! target))))

