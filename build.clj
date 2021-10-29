(ns build
  (:require [clojure.tools.build.api :as b]))
  (println "ha")

(def lib 'rtdraw)
(def version (format "1.0.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (println "ha")
  (clean nil)
  (println "le")

  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (println "basis:" basis)

  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})

  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis}))
