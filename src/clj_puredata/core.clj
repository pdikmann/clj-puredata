(ns clj-puredata.core
  "Collects all user-facing functions of the other namespaces, for easy import."
  (:require [clj-puredata.parse :as parse]
            [clj-puredata.translate :as translate]
            [clj-puredata.io :as io]
            [clj-puredata.misc :as misc]
            [potemkin :refer [import-vars]]))

(import-vars
 [clj-puredata.parse
  pd
  inlet
  outlet
  other
  connect]
 [clj-puredata.translate
  write-patch
  write-patch-reload]
 [clj-puredata.io
  open-pd
  load-patches
  reload-all-patches
  startup]
 [clj-puredata.misc
  color-file
  color-runtime
  hsl2rgb
  import-image])

(defn basic-usage []
  (open-pd)
  (Thread/sleep 3000)
  ;;(load-patch "test.pd")
  (write-patch-reload "test.pd"
                      [:text "Hello World"])
  ;; 4 - now edit the original WITH-PATCH, evaluate it, and see PureData update accordingly.
  ;; 5 - rinse and repeat.
  )
