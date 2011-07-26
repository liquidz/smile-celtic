(ns celtic.app_servlet
  (:gen-class :extends javax.servlet.http.HttpServlet)
  (:use celtic.core)
  (:use [appengine-magic.servlet :only [make-servlet-service-method]]))


(defn -service [this request response]
  ((make-servlet-service-method celtic-app) this request response))
