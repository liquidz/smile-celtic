(ns celtic.core
  (:use
    [compojure.core :only [GET POST defroutes wrap!]]
    [compojure.route :only [not-found]]
    [ring.util.response :only [redirect]]
    [ring.middleware.params :only [wrap-params]]
    [ring.middleware.keyword-params :only [wrap-keyword-params]]
    [ring.middleware.session :only [wrap-session]]
    [celtic html model])
  (:require
    [appengine-magic.core :as ae]
    [appengine-magic.services.user :as du]
    [appengine-magic.services.datastore :as ds]
    [clojure.contrib.string :as string]
    [clojure.contrib.json :as json]
    ))

(defn- toi [s] (Integer/parseInt s))

(defroutes app-handler
  ; fpp: feed per page
  (GET "/" {{:keys [page fpp rsspage] :or {page "1", fpp "3", rsspage "1"}} :params}
    (make-html :page (toi page) :fpp (toi fpp) :rsspage (toi rsspage)))
  (GET "/likes" {{:keys [page fpp] :or {page "1", fpp "3"}} :params}
    (if (du/user-logged-in?)
      (make-likes-html :page (toi page) :fpp (toi fpp))
      (redirect "/")))
  (GET "/dislikes" {{:keys [page fpp] :or {page "1", fpp "3"}} :params}
    (if (du/user-logged-in?)
      (make-dislikes-html :page (toi page) :fpp (toi fpp))
      (redirect "/")))
  (GET "/shuffle" {{:keys [fpp] :or {fpp "3"}} :params}
    (make-shuffle-html :fpp (toi fpp)))


  ;; API
  (GET "/set/like" {{key :key} :params}
    (json/json-str (if (and (not (string/blank? key)) (like key)) "ok" "ng")))
  (GET "/set/dislike" {{key :key} :params}
    (json/json-str (if (and (not (string/blank? key)) (dislike key)) "ok" "ng")))
  (GET "/cancel/like" {{key :key} :params}
    (json/json-str (if (and (not (string/blank? key)) (delete-like key)) "ok" "ng")))
  (GET "/cancel/dislike" {{key :key} :params}
    (json/json-str (if (and (not (string/blank? key)) (delete-dislike key)) "ok" "ng")))


  (not-found "page not found"))

(wrap! app-handler wrap-session wrap-keyword-params wrap-params)
(ae/def-appengine-app celtic-app #'app-handler)

