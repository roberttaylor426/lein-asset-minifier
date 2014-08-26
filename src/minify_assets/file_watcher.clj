(ns minify-assets.file-watcher
  (:require [clojure.set :refer [rename-keys]]
            [clojure.java.io :refer [file]]
            [clojure.core.async
             :as async
             :refer [go thread close! <! >! >!!]])
  (:import
       [java.nio.file
        FileSystems
        Path
        Paths
        StandardWatchEventKinds]
   java.util.concurrent.TimeUnit))

(defn event-modifier-high []
  (let [c (Class/forName "com.sun.nio.file.SensitivityWatchEventModifier")]
    (.get (.getField c "HIGH") c)))

(defn register-events! [dir watch-service]
  (.register dir
             watch-service
             (into-array
               [StandardWatchEventKinds/ENTRY_CREATE
                StandardWatchEventKinds/ENTRY_MODIFY
                StandardWatchEventKinds/ENTRY_DELETE
                StandardWatchEventKinds/OVERFLOW])
             (into-array [(event-modifier-high)])))

(defn watch-loop [watch-service c run?]
  (while @run?
      (when-let [k (.take watch-service)]
        (doseq [event (.pollEvents k)]
          (>!! c event))
        (when (.reset k)))))

(defn watch [path run?]
  (let [dir  (-> path (file) (.toURI) (Paths/get))
        c    (async/chan)]
    (thread
      (with-open [watch-service (.newWatchService (FileSystems/getDefault))]
       (register-events! dir watch-service)
       (watch-loop watch-service c run?)))
    c))

(defn start-watch! [path handler]
  (let [run? (atom true)
        c (watch "resources" run?)]
    (println "watching" path "for changes")
    (go
     (while @run?
      (let [e (<! c)]
        (handler e))))
    (fn []
      (reset! run? false)
      (close! c))))
